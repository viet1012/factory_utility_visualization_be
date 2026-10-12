package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.UtilityHistoryProperties;
import com.example.factory_utility_visualization_be.config.UtilitySignalKindProperties;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistoryPointDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistoryResponseDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistorySeriesDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalKind;
import com.example.factory_utility_visualization_be.exception.SignalHistoryBadRequestException;
import com.example.factory_utility_visualization_be.repository.runtime.SignalHistoryRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryMetaProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryRawBucketProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistorySnapshotProjection;
import com.example.factory_utility_visualization_be.service.runtime.UtilityAlertThresholdCacheService.AlertThreshold;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GET /api/utility/series/history.
 *
 * Source by range (bucket=auto):
 *   <= 1h  -> RAW, 1m  | <= 6h -> RAW, 5m | <= 24h -> RAW, 15m
 *   > 24h  -> HOURLY_SNAPSHOT (F2_Utility_Para_History_Main), 1h
 * Raw history only keeps ~24h: when from is older than the oldest raw
 * sample minus utility.history.raw-tolerance-minutes, auto falls back to
 * HOURLY_SNAPSHOT, manual 1h falls back to HOURLY_SNAPSHOT and manual
 * sub-hour buckets are rejected.
 */
@Service
@RequiredArgsConstructor
public class SignalHistoryService {

	// recorded_at / pick_at are stored as plant local time
	private static final ZoneId DB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final int MAX_SIGNALS = 50;
	private static final int MAX_POINTS_PER_SIGNAL = 1000;
	private static final Duration MAX_RANGE = Duration.ofDays(7);
	private static final Duration RAW_MAX_RANGE = Duration.ofHours(24);

	// SQL Server DATEDIFF(MINUTE, 0, x) counts from 1900-01-01; Java gap-fill
	// must floor on the same origin to produce the same bucket timestamps.
	private static final LocalDateTime BUCKET_ORIGIN = LocalDateTime.of(1900, 1, 1, 0, 0);

	private static final Pattern PLC_ADDRESS_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

	// Unencoded '+' in a query string arrives as a space: "08:00:00 07:00"
	private static final Pattern SPACE_BEFORE_OFFSET = Pattern.compile(" (\\d{2}:?\\d{2})$");

	private final SignalHistoryRepo historyRepo;
	private final UtilityAlertThresholdCacheService thresholdCache;
	private final UtilitySignalKindProperties kindProperties;
	private final RawHistoryWindowCacheService rawWindowCache;
	private final UtilityHistoryProperties historyProperties;

	private enum Source {
		RAW("MIN_AVG_MAX"),
		HOURLY_SNAPSHOT("SNAPSHOT");

		private final String aggregation;

		Source(String aggregation) {
			this.aggregation = aggregation;
		}
	}

	private record Plan(Source source, int bucketMinutes, String bucketLabel) {
	}

	// code is null when the para row has no real category (NULL / blank / '0')
	private record SignalMeta(String name, String code, String unit, SignalKind kind) {
	}

	private record BucketValues(
			BigDecimal min, BigDecimal avg, BigDecimal max, BigDecimal last, long count, BigDecimal delta) {
		private static final BucketValues EMPTY = new BucketValues(null, null, null, null, 0, null);
	}

	@Transactional(readOnly = true)
	public SignalHistoryResponseDto getHistory(
			String boxDeviceId,
			String plcAddressCsv,
			String from,
			String to,
			String bucket
	) {
		String box = requireText(boxDeviceId, "boxDeviceId is required");
		LocalDateTime fromTs = parseDateTime(from, "from");
		LocalDateTime toTs = parseDateTime(to, "to");

		if (!fromTs.isBefore(toTs)) {
			throw new SignalHistoryBadRequestException("from must be before to");
		}

		Duration range = Duration.between(fromTs, toTs);

		if (range.compareTo(MAX_RANGE) > 0) {
			throw new SignalHistoryBadRequestException("Range between from and to must not exceed 7 days");
		}

		Plan plan = resolvePlan(bucket, fromTs, range);
		List<LocalDateTime> bucketTimes = bucketTimes(fromTs, toTs, plan);

		if (bucketTimes.size() > MAX_POINTS_PER_SIGNAL) {
			throw new SignalHistoryBadRequestException(
					"bucket=" + plan.bucketLabel() + " gives " + bucketTimes.size()
							+ " points per signal (max " + MAX_POINTS_PER_SIGNAL
							+ "); use a larger bucket or bucket=auto"
			);
		}

		List<String> requestedPlcs = parsePlcAddresses(plcAddressCsv);

		if (requestedPlcs.size() > MAX_SIGNALS) {
			throw new SignalHistoryBadRequestException(
					"At most " + MAX_SIGNALS + " plcAddress values per request");
		}

		Map<String, SignalMeta> metaByPlc = loadMeta(box, requestedPlcs);

		List<String> plcs = requestedPlcs.isEmpty()
				? metaByPlc.keySet().stream().sorted(this::comparePlcAddress).toList()
				: requestedPlcs;

		if (plcs.size() > MAX_SIGNALS) {
			throw new SignalHistoryBadRequestException(
					"boxDeviceId " + box + " has " + plcs.size() + " signals (max " + MAX_SIGNALS
							+ " per request); pass plcAddress to select signals");
		}

		// plcAddress requested but not found in F2_Utility_Para
		SignalMeta unknownMeta = new SignalMeta(null, null, null, kindProperties.resolve(null, null));

		Map<String, Map<LocalDateTime, BucketValues>> valuesByPlc = plcs.isEmpty()
				? Map.of()
				: loadValues(box, plcs, toTs, plan, bucketTimes, metaByPlc, unknownMeta);

		Map<String, AlertThreshold> thresholds = thresholdCache.findActiveThresholdsByCode();

		List<SignalHistorySeriesDto> signals = plcs.stream()
				.map(plc -> toSeries(
						box,
						plc,
						metaByPlc.getOrDefault(plc, unknownMeta),
						valuesByPlc.getOrDefault(plc, Map.of()),
						bucketTimes,
						thresholds
				))
				.toList();

		return new SignalHistoryResponseDto(
				formatTs(fromTs),
				formatTs(toTs),
				plan.bucketLabel(),
				plan.source().name(),
				plan.source().aggregation,
				signals
		);
	}

	// ---------------------------------------------------------------------
	// Request parsing / planning
	// ---------------------------------------------------------------------

	private Plan resolvePlan(String bucket, LocalDateTime fromTs, Duration range) {
		String value = (bucket == null || bucket.isBlank()) ? "auto" : bucket.trim().toLowerCase(Locale.ROOT);

		return switch (value) {
			case "auto" -> autoPlan(fromTs, range);
			case "1m" -> rawPlan(fromTs, range, 1, value);
			case "5m" -> rawPlan(fromTs, range, 5, value);
			case "15m" -> rawPlan(fromTs, range, 15, value);
			// 1h within the raw window keeps real MIN/AVG/MAX
			case "1h" -> range.compareTo(RAW_MAX_RANGE) <= 0 && rawCovers(fromTs, rawTolerance())
					? new Plan(Source.RAW, 60, value)
					: new Plan(Source.HOURLY_SNAPSHOT, 60, value);
			default -> throw new SignalHistoryBadRequestException(
					"bucket must be one of: auto, 1m, 5m, 15m, 1h");
		};
	}

	// true when raw still holds data at fromTs (or the raw table is empty,
	// in which case there is nothing to fall back from)
	private boolean rawCovers(LocalDateTime fromTs, Duration tolerance) {
		return rawWindowCache.findOldestRawRecordedAt()
				.map(oldest -> !fromTs.isBefore(oldest.minus(tolerance)))
				.orElse(true);
	}

	private Duration rawTolerance() {
		return Duration.ofMinutes(historyProperties.getRawToleranceMinutes());
	}

	private Plan autoPlan(LocalDateTime fromTs, Duration range) {
		if (range.compareTo(RAW_MAX_RANGE) > 0 || !rawCovers(fromTs, rawTolerance())) {
			return new Plan(Source.HOURLY_SNAPSHOT, 60, "1h");
		}

		if (range.compareTo(Duration.ofHours(1)) <= 0) {
			return new Plan(Source.RAW, 1, "1m");
		}

		if (range.compareTo(Duration.ofHours(6)) <= 0) {
			return new Plan(Source.RAW, 5, "5m");
		}

		return new Plan(Source.RAW, 15, "15m");
	}

	private Plan rawPlan(LocalDateTime fromTs, Duration range, int bucketMinutes, String label) {
		if (range.compareTo(RAW_MAX_RANGE) > 0) {
			throw new SignalHistoryBadRequestException(
					"bucket=" + label + " needs raw data, which is only kept for ~24h; "
							+ "use bucket=1h or bucket=auto for ranges over 24h");
		}

		Optional<LocalDateTime> oldest = rawWindowCache.findOldestRawRecordedAt();

		if (oldest.isPresent() && fromTs.isBefore(oldest.get().minus(rawTolerance()))) {
			throw new SignalHistoryBadRequestException(
					"raw data only available from " + formatTs(oldest.get())
							+ "; use bucket=1h or bucket=auto for earlier data");
		}

		return new Plan(Source.RAW, bucketMinutes, label);
	}

	private LocalDateTime parseDateTime(String value, String field) {
		String text = requireText(
				value,
				field + " is required (ISO-8601, e.g. 2026-10-12T08:00:00 or 2026-10-12T08:00:00+07:00)");

		String normalized = SPACE_BEFORE_OFFSET.matcher(text).replaceFirst("+$1");

		try {
			return OffsetDateTime.parse(normalized)
					.atZoneSameInstant(DB_ZONE)
					.toLocalDateTime();
		} catch (DateTimeParseException ignored) {
			// no offset -> already DB local time
		}

		try {
			return LocalDateTime.parse(normalized);
		} catch (DateTimeParseException e) {
			throw new SignalHistoryBadRequestException(
					field + " is not a valid ISO-8601 date-time: '" + text
							+ "' (e.g. 2026-10-12T08:00:00 or 2026-10-12T08:00:00+07:00)");
		}
	}

	private List<String> parsePlcAddresses(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}

		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.toList();
	}

	private String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new SignalHistoryBadRequestException(message);
		}

		return value.trim();
	}

	// ---------------------------------------------------------------------
	// Data loading
	// ---------------------------------------------------------------------

	private Map<String, SignalMeta> loadMeta(String box, List<String> requestedPlcs) {
		List<SignalHistoryMetaProjection> rows = requestedPlcs.isEmpty()
				? historyRepo.findSignalMeta(box)
				: historyRepo.findSignalMetaByPlc(box, requestedPlcs);

		Map<String, SignalMeta> metaByPlc = new LinkedHashMap<>();

		for (SignalHistoryMetaProjection row : rows) {
			String code = normalizeCateId(row.getCateId());

			// duplicate para rows: keep the first (lowest id)
			metaByPlc.putIfAbsent(
					row.getPlcAddress(),
					new SignalMeta(
							row.getNameEn(),
							code,
							row.getUnit(),
							kindProperties.resolve(code, row.getNameEn())
					)
			);
		}

		return metaByPlc;
	}

	// NULL, blank and '0' mean "no category": no code, no threshold lookup
	private String normalizeCateId(String cateId) {
		if (cateId == null || cateId.isBlank() || "0".equals(cateId.trim())) {
			return null;
		}

		return cateId.trim();
	}

	private Map<String, Map<LocalDateTime, BucketValues>> loadValues(
			String box,
			List<String> plcs,
			LocalDateTime toTs,
			Plan plan,
			List<LocalDateTime> bucketTimes,
			Map<String, SignalMeta> metaByPlc,
			SignalMeta unknownMeta
	) {
		if (bucketTimes.isEmpty()) {
			return Map.of();
		}

		return plan.source() == Source.RAW
				? loadRaw(box, plcs, toTs, plan.bucketMinutes(), bucketTimes, metaByPlc, unknownMeta)
				: loadSnapshots(box, plcs, toTs, bucketTimes, metaByPlc, unknownMeta);
	}

	/**
	 * Queries from one bucket before the first bucket so the first COUNTER
	 * delta has a previous last value. Because the query starts on a bucket
	 * boundary, the first bucket holds the full bucket even when from is not
	 * aligned (same as every other bucket).
	 */
	private Map<String, Map<LocalDateTime, BucketValues>> loadRaw(
			String box,
			List<String> plcs,
			LocalDateTime toTs,
			int bucketMinutes,
			List<LocalDateTime> bucketTimes,
			Map<String, SignalMeta> metaByPlc,
			SignalMeta unknownMeta
	) {
		LocalDateTime queryFrom = bucketTimes.get(0).minusMinutes(bucketMinutes);

		Map<String, Map<LocalDateTime, SignalHistoryRawBucketProjection>> rowsByPlc = new HashMap<>();

		for (SignalHistoryRawBucketProjection row :
				historyRepo.findRawBuckets(box, plcs, queryFrom, toTs, bucketMinutes)) {
			rowsByPlc.computeIfAbsent(row.getPlcAddress(), key -> new HashMap<>())
					.put(row.getBucketAt(), row);
		}

		Map<String, Map<LocalDateTime, BucketValues>> result = new HashMap<>();

		rowsByPlc.forEach((plc, rows) -> {
			boolean counter = metaByPlc.getOrDefault(plc, unknownMeta).kind() == SignalKind.COUNTER;
			Map<LocalDateTime, BucketValues> values = new HashMap<>();

			for (LocalDateTime bucketAt : bucketTimes) {
				SignalHistoryRawBucketProjection row = rows.get(bucketAt);

				if (row == null) {
					continue;
				}

				SignalHistoryRawBucketProjection previous = rows.get(bucketAt.minusMinutes(bucketMinutes));

				BigDecimal delta = counter
						? counterDelta(row.getLastValue(), previous == null ? null : previous.getLastValue())
						: null;

				values.put(bucketAt, new BucketValues(
						row.getMinValue(),
						row.getAvgValue(),
						row.getMaxValue(),
						row.getLastValue(),
						row.getSampleCount() == null ? 0 : row.getSampleCount(),
						delta
				));
			}

			result.put(plc, values);
		});

		return result;
	}

	// null when either side is missing or the counter went down (reset)
	private BigDecimal counterDelta(BigDecimal current, BigDecimal previous) {
		if (current == null || previous == null) {
			return null;
		}

		BigDecimal delta = current.subtract(previous);
		return delta.signum() < 0 ? null : delta;
	}

	private Map<String, Map<LocalDateTime, BucketValues>> loadSnapshots(
			String box,
			List<String> plcs,
			LocalDateTime toTs,
			List<LocalDateTime> bucketTimes,
			Map<String, SignalMeta> metaByPlc,
			SignalMeta unknownMeta
	) {
		// one extra hour before the first bucket so COUNTER delta has a previous snapshot
		LocalDateTime queryFrom = bucketTimes.get(0).minusHours(1);

		Map<String, Map<LocalDateTime, BigDecimal>> snapshotsByPlc = new HashMap<>();

		for (SignalHistorySnapshotProjection row :
				historyRepo.findHourlySnapshots(box, plcs, queryFrom, toTs)) {
			snapshotsByPlc.computeIfAbsent(row.getPlcAddress(), key -> new HashMap<>())
					.put(row.getBucketAt(), row.getValue());
		}

		Map<String, Map<LocalDateTime, BucketValues>> result = new HashMap<>();

		snapshotsByPlc.forEach((plc, snapshots) -> {
			boolean counter = metaByPlc.getOrDefault(plc, unknownMeta).kind() == SignalKind.COUNTER;
			Map<LocalDateTime, BucketValues> values = new HashMap<>();

			for (LocalDateTime bucketAt : bucketTimes) {
				BigDecimal value = snapshots.get(bucketAt);

				if (value == null) {
					continue;
				}

				BigDecimal delta = counter
						? counterDelta(value, snapshots.get(bucketAt.minusHours(1)))
						: null;

				values.put(bucketAt, new BucketValues(null, value, null, value, 1, delta));
			}

			result.put(plc, values);
		});

		return result;
	}

	// ---------------------------------------------------------------------
	// Response building
	// ---------------------------------------------------------------------

	private SignalHistorySeriesDto toSeries(
			String box,
			String plc,
			SignalMeta meta,
			Map<LocalDateTime, BucketValues> values,
			List<LocalDateTime> bucketTimes,
			Map<String, AlertThreshold> thresholds
	) {
		AlertThreshold threshold = meta.code() == null
				? null
				: thresholds.get(UtilityAlertThresholdCacheService.normalize(meta.code()));

		List<SignalHistoryPointDto> points = new ArrayList<>(bucketTimes.size());

		for (LocalDateTime bucketAt : bucketTimes) {
			BucketValues bucket = values.getOrDefault(bucketAt, BucketValues.EMPTY);

			points.add(new SignalHistoryPointDto(
					formatTs(bucketAt),
					bucket.min(),
					bucket.avg(),
					bucket.max(),
					bucket.last(),
					bucket.count(),
					bucket.delta()
			));
		}

		return new SignalHistorySeriesDto(
				box,
				plc,
				meta.name(),
				meta.code(),
				meta.unit(),
				meta.kind(),
				threshold == null ? null : threshold.low(),
				threshold == null ? null : threshold.high(),
				threshold == null ? null : threshold.alertLevel(),
				points
		);
	}

	/**
	 * Gap-fill timeline over [from, to).
	 * RAW: first bucket is the one containing from (matches SQL flooring).
	 * HOURLY_SNAPSHOT: first snapshot at or after from, since a snapshot is a
	 * point in time and one taken before from is outside the range.
	 */
	private List<LocalDateTime> bucketTimes(LocalDateTime fromTs, LocalDateTime toTs, Plan plan) {
		int minutes = plan.bucketMinutes();
		LocalDateTime first = floorToBucket(fromTs, minutes);

		if (plan.source() == Source.HOURLY_SNAPSHOT && first.isBefore(fromTs)) {
			first = first.plusMinutes(minutes);
		}

		List<LocalDateTime> times = new ArrayList<>();

		for (LocalDateTime t = first; t.isBefore(toTs); t = t.plusMinutes(minutes)) {
			times.add(t);

			if (times.size() > MAX_POINTS_PER_SIGNAL) {
				break;
			}
		}

		return times;
	}

	private LocalDateTime floorToBucket(LocalDateTime value, int bucketMinutes) {
		long minutes = ChronoUnit.MINUTES.between(BUCKET_ORIGIN, value);
		return BUCKET_ORIGIN.plusMinutes(Math.floorDiv(minutes, bucketMinutes) * bucketMinutes);
	}

	private String formatTs(LocalDateTime value) {
		return value.atZone(DB_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	// D2 < D10 (natural order), otherwise case-insensitive text order
	private int comparePlcAddress(String first, String second) {
		Matcher firstMatcher = PLC_ADDRESS_PATTERN.matcher(first);
		Matcher secondMatcher = PLC_ADDRESS_PATTERN.matcher(second);

		if (!firstMatcher.matches() || !secondMatcher.matches()) {
			return first.compareToIgnoreCase(second);
		}

		return Comparator
				.comparing((Matcher m) -> m.group(1), String.CASE_INSENSITIVE_ORDER)
				.thenComparing(m -> Long.parseLong(m.group(2)))
				.compare(firstMatcher, secondMatcher);
	}
}
