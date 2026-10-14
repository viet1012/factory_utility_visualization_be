package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.UtilityHistoryProperties;
import com.example.factory_utility_visualization_be.config.UtilitySignalKindProperties;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistoryPointDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistoryResponseDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistorySeriesDto;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalKind;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalThresholdDto;
import com.example.factory_utility_visualization_be.exception.SignalHistoryBadRequestException;
import com.example.factory_utility_visualization_be.repository.runtime.SignalHistoryRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryDailyBucketProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryMetaProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryRawBucketProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistorySnapshotProjection;
import com.example.factory_utility_visualization_be.service.runtime.UtilityAlertThresholdCacheService.AlertRule;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 *
 * bucket=1d -> DAILY (F2_Utility_Para_History_Main), same rules as
 * /daily-signals, range up to 62 days; never chosen by auto.
 *
 * boxDeviceId may be repeated or CSV (max 30 boxes, max 300 signals in
 * total); each box is queried on its own so the box_device_id seek is kept.
 * A single box behaves exactly as before (max 50 signals, unknown
 * plcAddress returned with null meta); with several boxes, plcAddress is
 * applied to every box and only addresses present in that box's
 * F2_Utility_Para are returned.
 */
@Service
@RequiredArgsConstructor
public class SignalHistoryService {

	// recorded_at / pick_at are stored as plant local time
	private static final ZoneId DB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final int MAX_SIGNALS = 50;
	private static final int MAX_BOXES = 30;
	private static final int MAX_TOTAL_SIGNALS = 300;
	private static final int MAX_POINTS_PER_SIGNAL = 1000;
	private static final Duration MAX_RANGE = Duration.ofDays(7);
	private static final Duration DAILY_MAX_RANGE = Duration.ofDays(62);
	private static final Duration RAW_MAX_RANGE = Duration.ofHours(24);
	private static final int DAY_MINUTES = 24 * 60;

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
		// COUNTER: delta = consumption of the hour starting at ts
		HOURLY_SNAPSHOT("SNAPSHOT;COUNTER=HOURLY_CONSUMPTION"),
		// main value: avg; COUNTER: delta = SUM(value > 0) of the day
		DAILY("AVG_MIN_MAX_LAST;COUNTER=DAILY_SUM");

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

	// signals selected for one box
	private record BoxSelection(String box, Map<String, SignalMeta> metaByPlc, List<String> plcs) {
	}

	@Transactional(readOnly = true)
	public SignalHistoryResponseDto getHistory(
			List<String> boxDeviceIds,
			String plcAddressCsv,
			String from,
			String to,
			String bucket
	) {
		List<String> boxes = parseBoxDeviceIds(boxDeviceIds);

		if (boxes.size() > MAX_BOXES) {
			throw new SignalHistoryBadRequestException(
					"At most " + MAX_BOXES + " boxDeviceId values per request");
		}

		LocalDateTime fromTs = parseDateTime(from, "from");
		LocalDateTime toTs = parseDateTime(to, "to");

		if (!fromTs.isBefore(toTs)) {
			throw new SignalHistoryBadRequestException("from must be before to");
		}

		Duration range = Duration.between(fromTs, toTs);
		String bucketValue = normalizeBucket(bucket);

		if ("1d".equals(bucketValue)) {
			if (range.compareTo(DAILY_MAX_RANGE) > 0) {
				throw new SignalHistoryBadRequestException(
						"Range between from and to must not exceed 62 days for bucket=1d");
			}
		} else if (range.compareTo(MAX_RANGE) > 0) {
			throw new SignalHistoryBadRequestException("Range between from and to must not exceed 7 days");
		}

		Plan plan = resolvePlan(bucketValue, fromTs, range);
		List<LocalDateTime> bucketTimes = bucketTimes(fromTs, toTs, plan);

		if (bucketTimes.size() > MAX_POINTS_PER_SIGNAL) {
			throw new SignalHistoryBadRequestException(
					"bucket=" + plan.bucketLabel() + " gives " + bucketTimes.size()
							+ " points per signal (max " + MAX_POINTS_PER_SIGNAL
							+ "); use a larger bucket or bucket=auto"
			);
		}

		boolean singleBox = boxes.size() == 1;
		List<String> requestedPlcs = parsePlcAddresses(plcAddressCsv);
		int maxRequestedPlcs = singleBox ? MAX_SIGNALS : MAX_TOTAL_SIGNALS;

		if (requestedPlcs.size() > maxRequestedPlcs) {
			throw new SignalHistoryBadRequestException(
					"At most " + maxRequestedPlcs + " plcAddress values per request");
		}

		List<BoxSelection> selections = new ArrayList<>(boxes.size());
		int totalSignals = 0;

		for (String box : boxes) {
			Map<String, SignalMeta> metaByPlc = loadMeta(box, requestedPlcs);

			List<String> plcs;

			if (requestedPlcs.isEmpty()) {
				plcs = metaByPlc.keySet().stream().sorted(this::comparePlcAddress).toList();
			} else if (singleBox) {
				plcs = requestedPlcs;
			} else {
				plcs = requestedPlcs.stream().filter(metaByPlc::containsKey).toList();
			}

			if (singleBox && plcs.size() > MAX_SIGNALS) {
				throw new SignalHistoryBadRequestException(
						"boxDeviceId " + box + " has " + plcs.size() + " signals (max " + MAX_SIGNALS
								+ " per request); pass plcAddress to select signals");
			}

			totalSignals += plcs.size();
			selections.add(new BoxSelection(box, metaByPlc, plcs));
		}

		if (totalSignals > MAX_TOTAL_SIGNALS) {
			throw new SignalHistoryBadRequestException(
					boxes.size() + " boxDeviceId values give " + totalSignals + " signals (max "
							+ MAX_TOTAL_SIGNALS + " per request); pass plcAddress or fewer boxDeviceId");
		}

		// plcAddress requested but not found in F2_Utility_Para
		SignalMeta unknownMeta = new SignalMeta(null, null, null, kindProperties.resolve(null, null));

		Map<String, AlertThreshold> thresholds = thresholdCache.findActiveThresholdsByCode();
		Map<String, List<AlertRule>> rules = thresholdCache.findActiveThresholdRulesByCode();

		List<SignalHistorySeriesDto> signals = new ArrayList<>(totalSignals);

		for (BoxSelection selection : selections) {
			Map<String, Map<LocalDateTime, BucketValues>> valuesByPlc = selection.plcs().isEmpty()
					? Map.of()
					: loadValues(selection.box(), selection.plcs(), toTs, plan, bucketTimes,
					selection.metaByPlc(), unknownMeta);

			for (String plc : selection.plcs()) {
				signals.add(toSeries(
						selection.box(),
						plc,
						selection.metaByPlc().getOrDefault(plc, unknownMeta),
						valuesByPlc.getOrDefault(plc, Map.of()),
						bucketTimes,
						thresholds,
						rules
				));
			}
		}

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

	private String normalizeBucket(String bucket) {
		return (bucket == null || bucket.isBlank()) ? "auto" : bucket.trim().toLowerCase(Locale.ROOT);
	}

	private Plan resolvePlan(String value, LocalDateTime fromTs, Duration range) {
		return switch (value) {
			case "auto" -> autoPlan(fromTs, range);
			case "1m" -> rawPlan(fromTs, range, 1, value);
			case "5m" -> rawPlan(fromTs, range, 5, value);
			case "15m" -> rawPlan(fromTs, range, 15, value);
			// 1h within the raw window keeps real MIN/AVG/MAX
			case "1h" -> range.compareTo(RAW_MAX_RANGE) <= 0 && rawCovers(fromTs, rawTolerance())
					? new Plan(Source.RAW, 60, value)
					: new Plan(Source.HOURLY_SNAPSHOT, 60, value);
			// no raw-window check: days older than History_Main keeps are just empty buckets
			case "1d" -> new Plan(Source.DAILY, DAY_MINUTES, value);
			default -> throw new SignalHistoryBadRequestException(
					"bucket must be one of: auto, 1m, 5m, 15m, 1h, 1d");
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

	// repeated params and CSV values, trimmed, first occurrence order
	private List<String> parseBoxDeviceIds(List<String> values) {
		List<String> boxes = values == null
				? List.of()
				: values.stream()
						.filter(Objects::nonNull)
						.flatMap(value -> Arrays.stream(value.split(",")))
						.map(String::trim)
						.filter(value -> !value.isEmpty())
						.distinct()
						.toList();

		if (boxes.isEmpty()) {
			throw new SignalHistoryBadRequestException("boxDeviceId is required");
		}

		return boxes;
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

		return switch (plan.source()) {
			case RAW -> loadRaw(box, plcs, toTs, plan.bucketMinutes(), bucketTimes, metaByPlc, unknownMeta);
			case HOURLY_SNAPSHOT -> loadSnapshots(box, plcs, toTs, bucketTimes, metaByPlc, unknownMeta);
			case DAILY -> loadDaily(box, plcs, toTs, bucketTimes, metaByPlc, unknownMeta);
		};
	}

	/**
	 * One bucket per local day, from the start of the day containing from.
	 * Readings (min/avg/max/last) use cur_value (value when null).
	 * ANALOG: day = CAST(pick_at AS DATE).
	 * COUNTER: day = CAST(pick_at - 1h AS DATE), since a row holds the hour
	 * ending at pick_at (pick_at 00:00 belongs to the previous day); last =
	 * cumulative at the end of the day, delta = SUM(value > 0) of the day.
	 */
	private Map<String, Map<LocalDateTime, BucketValues>> loadDaily(
			String box,
			List<String> plcs,
			LocalDateTime toTs,
			List<LocalDateTime> bucketTimes,
			Map<String, SignalMeta> metaByPlc,
			SignalMeta unknownMeta
	) {
		Map<Boolean, List<String>> plcsByCounter = plcs.stream()
				.collect(Collectors.partitioningBy(
						plc -> metaByPlc.getOrDefault(plc, unknownMeta).kind() == SignalKind.COUNTER));

		LocalDateTime firstDay = bucketTimes.get(0);
		Map<String, Map<LocalDateTime, BucketValues>> result = new HashMap<>();

		List<String> analogPlcs = plcsByCounter.get(false);

		if (!analogPlcs.isEmpty()) {
			putDailyRows(result, historyRepo.findDailyBuckets(box, analogPlcs, firstDay, toTs, 0), false);
		}

		List<String> counterPlcs = plcsByCounter.get(true);

		if (!counterPlcs.isEmpty()) {
			putDailyRows(result, historyRepo.findDailyBuckets(
					box, counterPlcs, firstDay.plusHours(1), toTs.plusHours(1), -1), true);
		}

		return result;
	}

	private void putDailyRows(
			Map<String, Map<LocalDateTime, BucketValues>> result,
			List<SignalHistoryDailyBucketProjection> rows,
			boolean counter
	) {
		for (SignalHistoryDailyBucketProjection row : rows) {
			BigDecimal delta = counter && row.getPositiveCount() != null && row.getPositiveCount() > 0
					? row.getPositiveSum()
					: null;

			result.computeIfAbsent(row.getPlcAddress(), key -> new HashMap<>())
					.put(row.getBucketAt(), new BucketValues(
							row.getMinValue(),
							row.getAvgValue(),
							row.getMaxValue(),
							row.getLastValue(),
							row.getSampleCount() == null ? 0 : row.getSampleCount(),
							delta
					));
		}
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

	/**
	 * History_Main row: cur_value = reading at pick_at (cumulative for
	 * COUNTER), value = cur_value - prev_value for COUNTER, i.e. consumption
	 * of the hour ENDING at pick_at.
	 * ANALOG: bucket = pick_at hour, avg = last = reading.
	 * COUNTER: bucket = pick_at hour - 1h (the hour the consumption belongs
	 * to), avg = last = reading at the end of that hour, delta = value
	 * (negative -> null). Queried up to one hour past to for that reason.
	 */
	private Map<String, Map<LocalDateTime, BucketValues>> loadSnapshots(
			String box,
			List<String> plcs,
			LocalDateTime toTs,
			List<LocalDateTime> bucketTimes,
			Map<String, SignalMeta> metaByPlc,
			SignalMeta unknownMeta
	) {
		Set<LocalDateTime> bucketSet = new HashSet<>(bucketTimes);
		Map<String, Map<LocalDateTime, BucketValues>> result = new HashMap<>();

		for (SignalHistorySnapshotProjection row :
				historyRepo.findHourlySnapshots(box, plcs, bucketTimes.get(0), toTs.plusHours(1))) {
			boolean counter = metaByPlc.getOrDefault(row.getPlcAddress(), unknownMeta).kind() == SignalKind.COUNTER;
			LocalDateTime bucketAt = counter ? row.getBucketAt().minusHours(1) : row.getBucketAt();

			if (!bucketSet.contains(bucketAt) || row.getValue() == null) {
				continue;
			}

			BigDecimal delta = counter ? nonNegative(row.getConsumption()) : null;

			result.computeIfAbsent(row.getPlcAddress(), key -> new HashMap<>())
					.put(bucketAt, new BucketValues(null, row.getValue(), null, row.getValue(), 1, delta));
		}

		return result;
	}

	private BigDecimal nonNegative(BigDecimal value) {
		return value == null || value.signum() < 0 ? null : value;
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
			Map<String, AlertThreshold> thresholds,
			Map<String, List<AlertRule>> rules
	) {
		String codeKey = meta.code() == null ? null : UtilityAlertThresholdCacheService.normalize(meta.code());
		AlertThreshold threshold = codeKey == null ? null : thresholds.get(codeKey);
		List<AlertRule> codeRules = codeKey == null ? List.of() : rules.getOrDefault(codeKey, List.of());

		boolean nonNegative = values.values()
				.stream()
				.map(this::lowValue)
				.filter(Objects::nonNull)
				.allMatch(value -> value.signum() >= 0);

		List<SignalThresholdDto> thresholdDtos = new ArrayList<>(codeRules.size());
		List<AlertRule> breachRules = new ArrayList<>(codeRules.size());

		for (AlertRule rule : codeRules) {
			boolean trivial = isTrivial(rule, nonNegative);

			thresholdDtos.add(new SignalThresholdDto(
					rule.level(), rule.low(), rule.high(), rule.ruleName(), rule.description(), trivial));

			if (!trivial) {
				breachRules.add(rule);
			}
		}

		List<SignalHistoryPointDto> points = new ArrayList<>(bucketTimes.size());
		int breachCount = 0;
		LocalDateTime lastBreachAt = null;

		for (LocalDateTime bucketAt : bucketTimes) {
			BucketValues bucket = values.getOrDefault(bucketAt, BucketValues.EMPTY);

			if (isBreach(bucket, breachRules)) {
				breachCount++;
				lastBreachAt = bucketAt;
			}

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
				thresholdDtos,
				breachCount,
				lastBreachAt == null ? null : formatTs(lastBreachAt),
				points
		);
	}

	// low = 0 with no high limit can never fire on non-negative data
	private boolean isTrivial(AlertRule rule, boolean nonNegative) {
		return nonNegative
				&& rule.high() == null
				&& rule.low() != null
				&& rule.low().signum() == 0;
	}

	private boolean isBreach(BucketValues bucket, List<AlertRule> rules) {
		BigDecimal low = lowValue(bucket);
		BigDecimal high = highValue(bucket);

		for (AlertRule rule : rules) {
			if (rule.high() != null && high != null && high.compareTo(rule.high()) > 0) {
				return true;
			}

			if (rule.low() != null && low != null && low.compareTo(rule.low()) < 0) {
				return true;
			}
		}

		return false;
	}

	// HOURLY_SNAPSHOT has no min/max: fall back to the snapshot (avg)
	private BigDecimal lowValue(BucketValues bucket) {
		return bucket.min() != null ? bucket.min() : bucket.avg();
	}

	private BigDecimal highValue(BucketValues bucket) {
		return bucket.max() != null ? bucket.max() : bucket.avg();
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
