package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.config.UtilitySignalKindProperties;
import com.example.factory_utility_visualization_be.config.WaterCheckProperties;
import com.example.factory_utility_visualization_be.dto.runtime.history.SignalKind;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckFacilityDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckGroupDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckIssueDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckResponseDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckSignalDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckStatus;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckSummaryDto;
import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckThresholdDto;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaHistoryRepo;
import com.example.factory_utility_visualization_be.repository.projection.LatestRecordView;
import com.example.factory_utility_visualization_be.repository.runtime.SignalHistoryRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.WaterCheckLastSeenProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.WaterCheckMetaProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.WaterCheckRawBucketProjection;
import com.example.factory_utility_visualization_be.service.runtime.UtilityAlertThresholdCacheService.AlertRule;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GET /api/utility/water-check.
 *
 * Scope: every ANALOG signal (UtilitySignalKindProperties, same resolver as
 * /latest) of the master rows (f2_utility_para of the channels whose
 * scada_channel.cate = 'Water', same filter as /latest?cate=Water), with
 * the latest raw value joined in. STATUS / COUNTER signals are left out.
 * A signal without a raw sample in the last 24h has value = null and is
 * STALE; its lastValue / lastSeenAt come from F2_Utility_Para_History_Main.
 *
 * Status (first match wins):
 *   STALE        no value, or latest sample older than 5 minutes
 *   SENSOR_FAULT pressure signal below utility.water-check.pump-stopped-min
 *   STOPPED      pressure signal in [pump-stopped-min, pump-stopped-max],
 *                whatever the limits (also when min > pump-stopped-max)
 *   OVER         value outside a non-trivial limit (same strict compare as
 *                /series/history breach)
 *   NEAR         distance to the nearest limit < 10% of (high - low), or
 *                < 10% of |value| when only one side is set
 *   OK / NO_THRESHOLD (no non-trivial THRESHOLD rule for the code)
 * Pressure signal: unit kg/cm2 (any spelling) or bar, or code containing
 * PRES. STOPPED is not an issue; SENSOR_FAULT is.
 *
 * Queries per (uncached) request: master + latestPerKey + raw buckets (one
 * per 500 signals) + History_Main last seen (one per 500 signals without
 * raw, none when every signal has raw); thresholds come from the 60s alert
 * cache. Whole response cached 30s.
 */
@Service
@RequiredArgsConstructor
public class WaterCheckService {

	// recorded_at is stored as plant local time
	private static final ZoneId DB_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	// value the /latest category filter receives (scada_channel.cate)
	private static final String WATER_CATE = "Water";

	private static final int STALE_MINUTES = 5;
	private static final int TREND_MINUTES = 60;
	private static final int SINCE_LOOKBACK_HOURS = 24;
	private static final BigDecimal NEAR_RATIO = new BigDecimal("0.1");

	// SQL Server IN (:list) does not accept an empty list
	private static final String NO_KEY = "__NO_KEY__";

	// (box, plc) pairs per query: box + plc + key lists stay well under the
	// 2100 parameters SQL Server accepts
	private static final int PAIRS_PER_QUERY = 500;

	private static final Pattern PLC_ADDRESS_PATTERN = Pattern.compile("^([A-Za-z]+)(\\d+)$");

	// kg/cm2, kg/cm², kgf/cm2, ... (spaces removed, lower case)
	private static final Pattern PRESSURE_UNIT_PATTERN = Pattern.compile("^kgf?/cm(2|²|\\^2)$|^bar$");

	private static final String REASON_STOPPED = "Pump stopped";
	private static final String REASON_SENSOR_FAULT = "Negative reading: sensor fault";

	private final F2UtilityParaHistoryRepo latestRepo;
	private final SignalHistoryRepo historyRepo;
	private final UtilityAlertThresholdCacheService thresholdCache;
	private final UtilitySignalKindProperties kindProperties;
	private final WaterCheckProperties waterCheckProperties;

	// code is null when the para row has no real category (NULL / blank / '0');
	// value / recordedAt null when there is no raw sample in the last 24h
	private record Signal(
			String fac,
			String box,
			String deviceName,
			String plc,
			String code,
			String name,
			String unit,
			BigDecimal value,
			LocalDateTime recordedAt,
			BigDecimal lastValue,
			LocalDateTime lastSeenAt
	) {
		String key() {
			return box + "|" + plc;
		}
	}

	// code groups: (code, unit); no-category groups: (name, unit)
	private record GroupKey(String code, String name, String unit) {
	}

	private record Evaluation(WaterCheckStatus status, BigDecimal distance) {
	}

	private record Limits(BigDecimal low, BigDecimal high) {
		private static final Limits NONE = new Limits(null, null);

		boolean isEmpty() {
			return low == null && high == null;
		}
	}

	private record Evaluated(Signal signal, Limits limits, boolean pressure, Evaluation evaluation) {
	}

	// issue row before formatting, so it can be sorted on since
	private record Issue(Evaluated item, BigDecimal trend1h, LocalDateTime since) {
	}

	@Cacheable(
			cacheNames = UtilityCacheNames.WATER_CHECK,
			key = "'all'",
			sync = true
	)
	@Transactional(readOnly = true)
	public WaterCheckResponseDto getWaterCheck() {
		LocalDateTime now = LocalDateTime.now(DB_ZONE).truncatedTo(ChronoUnit.SECONDS);

		List<Signal> signals = loadSignals(now);
		Map<String, List<AlertRule>> rulesByCode = thresholdCache.findActiveThresholdRulesByCode();

		// fac -> group -> signals, all sorted
		Map<String, Map<GroupKey, List<Signal>>> tree = new TreeMap<>();

		for (Signal signal : signals) {
			GroupKey key = signal.code() != null
					? new GroupKey(UtilityAlertThresholdCacheService.normalize(signal.code()), null, signal.unit())
					: new GroupKey(null, signal.name(), signal.unit());

			tree.computeIfAbsent(signal.fac(), fac -> new TreeMap<>(groupOrder()))
					.computeIfAbsent(key, k -> new ArrayList<>())
					.add(signal);
		}

		// pass 1: thresholds and status from the latest value
		Map<String, Map<GroupKey, List<WaterCheckThresholdDto>>> thresholdsByGroup = new HashMap<>();
		Map<String, Evaluated> evaluatedByKey = new LinkedHashMap<>();
		Map<String, List<AlertRule>> breachRulesByKey = new HashMap<>();

		tree.forEach((fac, groups) -> groups.forEach((groupKey, groupSignals) -> {
			groupSignals.sort(Comparator
					.comparing(Signal::box)
					.thenComparing(Signal::plc, this::comparePlcAddress));

			List<AlertRule> rules = groupKey.code() == null
					? List.of()
					: rulesByCode.getOrDefault(groupKey.code(), List.of());

			// pressure readings in the stopped / sensor-fault range do not
			// make a low = 0 rule non-trivial
			boolean nonNegative = groupSignals.stream()
					.filter(signal -> signal.value() != null)
					.filter(signal -> !isPressure(signal)
							|| signal.value().compareTo(waterCheckProperties.getPumpStoppedMax()) > 0)
					.allMatch(signal -> signal.value().signum() >= 0);

			List<WaterCheckThresholdDto> thresholdDtos = new ArrayList<>(rules.size());
			List<AlertRule> breachRules = new ArrayList<>(rules.size());

			for (AlertRule rule : rules) {
				boolean trivial = isTrivial(rule, nonNegative);
				thresholdDtos.add(new WaterCheckThresholdDto(rule.level(), rule.low(), rule.high(), trivial));

				if (!trivial) {
					breachRules.add(rule);
				}
			}

			thresholdsByGroup.computeIfAbsent(fac, f -> new HashMap<>()).put(groupKey, thresholdDtos);

			Limits limits = limits(breachRules);

			for (Signal signal : groupSignals) {
				boolean pressure = isPressure(signal);
				evaluatedByKey.put(signal.key(), new Evaluated(
						signal, limits, pressure, evaluateCurrent(signal, limits, pressure, now)));
				breachRulesByKey.put(signal.key(), breachRules);
			}
		}));

		// pass 2: one raw query for trend1h (all) and since (OVER / NEAR)
		Map<String, NavigableMap<LocalDateTime, BigDecimal>> bucketsByKey = loadRawBuckets(evaluatedByKey, now);

		Map<String, BigDecimal> trendByKey = new HashMap<>();
		Map<String, LocalDateTime> sinceByKey = new HashMap<>();

		evaluatedByKey.forEach((key, item) -> {
			NavigableMap<LocalDateTime, BigDecimal> buckets = bucketsByKey.getOrDefault(key, new TreeMap<>());
			trendByKey.put(key, trend1h(item.signal(), buckets, now));
			sinceByKey.put(key, since(item, buckets));
		});

		// response
		Map<WaterCheckStatus, Long> counts = new EnumMap<>(WaterCheckStatus.class);
		List<Issue> issues = new ArrayList<>();
		List<WaterCheckFacilityDto> facilities = new ArrayList<>(tree.size());

		tree.forEach((fac, groups) -> {
			List<WaterCheckGroupDto> groupDtos = new ArrayList<>(groups.size());

			groups.forEach((groupKey, groupSignals) -> {
				List<WaterCheckSignalDto> signalDtos = new ArrayList<>(groupSignals.size());

				for (Signal signal : groupSignals) {
					Evaluated item = evaluatedByKey.get(signal.key());
					WaterCheckStatus status = item.evaluation().status();
					BigDecimal trend = trendByKey.get(signal.key());
					LocalDateTime since = sinceByKey.get(signal.key());

					counts.merge(status, 1L, Long::sum);

					if (isIssue(status)) {
						issues.add(new Issue(item, trend, since));
					}

					signalDtos.add(new WaterCheckSignalDto(
							signal.box(),
							signal.deviceName(),
							signal.plc(),
							signal.name(),
							signal.value(),
							formatTs(signal.recordedAt()),
							signal.lastValue(),
							formatTs(signal.lastSeenAt()),
							status,
							reason(status),
							item.evaluation().distance(),
							trend,
							formatTs(since)
					));
				}

				Signal first = groupSignals.get(0);

				groupDtos.add(new WaterCheckGroupDto(
						first.code(),
						first.name(),
						groupKey.unit(),
						thresholdsByGroup.get(fac).get(groupKey),
						signalDtos
				));
			});

			facilities.add(new WaterCheckFacilityDto(fac, groupDtos));
		});

		issues.sort(Comparator
				.comparing((Issue issue) -> issue.item().evaluation().status())
				.thenComparing(Issue::since, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(issue -> issue.item().signal().fac())
				.thenComparing(issue -> issue.item().signal().box())
				.thenComparing(issue -> issue.item().signal().plc(), this::comparePlcAddress));

		return new WaterCheckResponseDto(
				formatTs(now),
				new WaterCheckSummaryDto(
						counts.getOrDefault(WaterCheckStatus.OK, 0L),
						counts.getOrDefault(WaterCheckStatus.NEAR, 0L),
						counts.getOrDefault(WaterCheckStatus.OVER, 0L),
						counts.getOrDefault(WaterCheckStatus.STALE, 0L),
						counts.getOrDefault(WaterCheckStatus.NO_THRESHOLD, 0L),
						counts.getOrDefault(WaterCheckStatus.STOPPED, 0L),
						counts.getOrDefault(WaterCheckStatus.SENSOR_FAULT, 0L)
				),
				facilities,
				issues.stream().map(this::toIssueDto).toList()
		);
	}

	// ---------------------------------------------------------------------
	// Data loading
	// ---------------------------------------------------------------------

	private record MasterRow(
			String fac, String box, String deviceName, String plc, String code, String name, String unit) {
		String key() {
			return box + "|" + plc;
		}
	}

	private record LastSeen(BigDecimal value, LocalDateTime at) {
	}

	/**
	 * Master rows are the base list; latestPerKey (same cate filter, as
	 * /latest?cate=Water) supplies value / recordedAt. A latest sample older
	 * than 24h counts as no raw sample.
	 */
	private List<Signal> loadSignals(LocalDateTime now) {
		// a box in several scada channels / duplicate para rows: keep the first
		Map<String, MasterRow> masterByKey = new LinkedHashMap<>();

		for (WaterCheckMetaProjection row : historyRepo.findWaterCheckMeta(WATER_CATE)) {
			String box = trimToNull(row.getBoxDeviceId());
			String plc = trimToNull(row.getPlcAddress());

			if (box == null || plc == null
					|| kindProperties.resolveRaw(row.getCateId(), row.getNameEn()) != SignalKind.ANALOG) {
				continue;
			}

			MasterRow master = new MasterRow(
					safeText(row.getFac()),
					box,
					trimToNull(row.getBoxId()),
					plc,
					normalizeCateId(row.getCateId()),
					trimToNull(row.getNameEn()),
					trimToNull(row.getUnit())
			);

			masterByKey.putIfAbsent(master.key(), master);
		}

		if (masterByKey.isEmpty()) {
			return List.of();
		}

		LocalDateTime rawFrom = now.minusHours(SINCE_LOOKBACK_HOURS);
		Map<String, LatestRecordView> latestByKey = new HashMap<>();

		for (LatestRecordView row : latestRepo.latestPerKey(
				null,
				null,
				WATER_CATE,
				null,
				0,
				List.of(NO_KEY),
				0,
				List.of(NO_KEY)
		)) {
			String box = trimToNull(row.getBoxDeviceId());
			String plc = trimToNull(row.getPlcAddress());

			if (box != null && plc != null && !row.getRecordedAt().isBefore(rawFrom)) {
				latestByKey.putIfAbsent(box + "|" + plc, row);
			}
		}

		List<MasterRow> missing = masterByKey.values()
				.stream()
				.filter(master -> !latestByKey.containsKey(master.key()))
				.toList();

		Map<String, LastSeen> lastSeenByKey = loadLastSeen(missing);

		List<Signal> signals = new ArrayList<>(masterByKey.size());

		for (MasterRow master : masterByKey.values()) {
			LatestRecordView latest = latestByKey.get(master.key());
			LastSeen lastSeen = latest != null
					? new LastSeen(latest.getValue(), latest.getRecordedAt())
					: lastSeenByKey.get(master.key());

			signals.add(new Signal(
					master.fac(),
					master.box(),
					master.deviceName(),
					master.plc(),
					master.code(),
					master.name(),
					master.unit(),
					latest == null ? null : latest.getValue(),
					latest == null ? null : latest.getRecordedAt(),
					lastSeen == null ? null : lastSeen.value(),
					lastSeen == null ? null : lastSeen.at()
			));
		}

		return signals;
	}

	// latest History_Main snapshot of signals without raw in the last 24h
	private Map<String, LastSeen> loadLastSeen(List<MasterRow> missing) {
		Map<String, LastSeen> result = new HashMap<>();

		for (List<MasterRow> batch : batches(missing)) {
			Set<String> boxes = new LinkedHashSet<>();
			Set<String> plcs = new LinkedHashSet<>();
			List<String> keys = new ArrayList<>(batch.size());

			for (MasterRow master : batch) {
				boxes.add(master.box());
				plcs.add(master.plc());
				keys.add(master.key());
			}

			for (WaterCheckLastSeenProjection row : historyRepo.findWaterCheckLastSeen(
					new ArrayList<>(boxes), new ArrayList<>(plcs), keys)) {
				result.putIfAbsent(
						row.getBoxDeviceId() + "|" + row.getPlcAddress(),
						new LastSeen(row.getLastValue(), row.getPickAt()));
			}
		}

		return result;
	}

	/**
	 * 1-minute last values: from now - 65 min for every signal (trend1h),
	 * from now - 24h for OVER / NEAR / SENSOR_FAULT signals (since). Signals without a raw
	 * sample in the last 24h are skipped; one query per PAIRS_PER_QUERY.
	 */
	private Map<String, NavigableMap<LocalDateTime, BigDecimal>> loadRawBuckets(
			Map<String, Evaluated> evaluatedByKey,
			LocalDateTime now
	) {
		List<Evaluated> withRaw = evaluatedByKey.values()
				.stream()
				.filter(item -> item.signal().recordedAt() != null)
				.toList();

		LocalDateTime fromTs = floorMinute(now.minusHours(SINCE_LOOKBACK_HOURS));
		LocalDateTime recentFromTs = floorMinute(now.minusMinutes(TREND_MINUTES + STALE_MINUTES));

		Map<String, NavigableMap<LocalDateTime, BigDecimal>> result = new HashMap<>();

		for (List<Evaluated> batch : batches(withRaw)) {
			Set<String> boxes = new LinkedHashSet<>();
			Set<String> plcs = new LinkedHashSet<>();
			Set<String> batchKeys = new HashSet<>();
			List<String> longKeys = new ArrayList<>();

			for (Evaluated item : batch) {
				String key = item.signal().key();

				boxes.add(item.signal().box());
				plcs.add(item.signal().plc());
				batchKeys.add(key);

				if (tracksSince(item.evaluation().status())) {
					longKeys.add(key);
				}
			}

			for (WaterCheckRawBucketProjection row : historyRepo.findWaterCheckRawBuckets(
					new ArrayList<>(boxes),
					new ArrayList<>(plcs),
					fromTs,
					recentFromTs,
					longKeys.isEmpty() ? List.of(NO_KEY) : longKeys
			)) {
				String key = row.getBoxDeviceId() + "|" + row.getPlcAddress();

				// box/plc IN lists are a superset of the batch's real pairs
				if (batchKeys.contains(key)) {
					result.computeIfAbsent(key, k -> new TreeMap<>()).put(row.getBucketAt(), row.getLastValue());
				}
			}
		}

		return result;
	}

	private <T> List<List<T>> batches(List<T> items) {
		List<List<T>> result = new ArrayList<>();

		for (int start = 0; start < items.size(); start += PAIRS_PER_QUERY) {
			result.add(items.subList(start, Math.min(start + PAIRS_PER_QUERY, items.size())));
		}

		return result;
	}

	// ---------------------------------------------------------------------
	// Evaluation
	// ---------------------------------------------------------------------

	// same rule as /series/history: low = 0 with no high limit never fires
	// on non-negative data
	private boolean isTrivial(AlertRule rule, boolean nonNegative) {
		return nonNegative
				&& rule.high() == null
				&& rule.low() != null
				&& rule.low().signum() == 0;
	}

	// innermost limits: breaching any rule = breaching the highest low or
	// the lowest high
	private Limits limits(List<AlertRule> breachRules) {
		BigDecimal low = breachRules.stream()
				.map(AlertRule::low)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);

		BigDecimal high = breachRules.stream()
				.map(AlertRule::high)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder())
				.orElse(null);

		return low == null && high == null ? Limits.NONE : new Limits(low, high);
	}

	private Evaluation evaluateCurrent(Signal signal, Limits limits, boolean pressure, LocalDateTime now) {
		boolean stale = signal.value() == null
				|| signal.recordedAt().isBefore(now.minusMinutes(STALE_MINUTES));

		Evaluation byValue = signal.value() == null
				? new Evaluation(limits.isEmpty() ? WaterCheckStatus.NO_THRESHOLD : WaterCheckStatus.OK, null)
				: evaluate(signal.value(), limits, pressure);

		return stale ? new Evaluation(WaterCheckStatus.STALE, byValue.distance()) : byValue;
	}

	// SENSOR_FAULT > STOPPED > limits status (OVER > NEAR > OK / NO_THRESHOLD).
	// A pressure value in the pump-stopped range is always STOPPED, even when
	// the limit min lies above pump-stopped-max.
	private Evaluation evaluate(BigDecimal value, Limits limits, boolean pressure) {
		if (pressure && value.compareTo(waterCheckProperties.getPumpStoppedMin()) < 0) {
			return new Evaluation(WaterCheckStatus.SENSOR_FAULT, null);
		}

		Evaluation byLimits = evaluateLimits(value, limits);

		if (pressure && value.compareTo(waterCheckProperties.getPumpStoppedMax()) <= 0) {
			return new Evaluation(WaterCheckStatus.STOPPED, byLimits.distance());
		}

		return byLimits;
	}

	private Evaluation evaluateLimits(BigDecimal value, Limits limits) {
		if (limits.isEmpty()) {
			return new Evaluation(WaterCheckStatus.NO_THRESHOLD, null);
		}

		BigDecimal toLow = limits.low() == null ? null : value.subtract(limits.low());
		BigDecimal toHigh = limits.high() == null ? null : limits.high().subtract(value);

		BigDecimal distance = toLow == null ? toHigh
				: toHigh == null ? toLow
				: toLow.min(toHigh);

		if (distance.signum() < 0) {
			return new Evaluation(WaterCheckStatus.OVER, distance);
		}

		BigDecimal margin = limits.low() != null && limits.high() != null
				? limits.high().subtract(limits.low()).abs().multiply(NEAR_RATIO)
				: value.abs().multiply(NEAR_RATIO);

		return new Evaluation(
				distance.compareTo(margin) < 0 ? WaterCheckStatus.NEAR : WaterCheckStatus.OK,
				distance
		);
	}

	// value now - last value of the latest bucket at or before now - 60 min
	// (bucket no older than STALE_MINUTES before that)
	private BigDecimal trend1h(
			Signal signal,
			NavigableMap<LocalDateTime, BigDecimal> buckets,
			LocalDateTime now
	) {
		if (signal.value() == null) {
			return null;
		}

		LocalDateTime target = floorMinute(now.minusMinutes(TREND_MINUTES));
		Map.Entry<LocalDateTime, BigDecimal> past = buckets.floorEntry(target);

		if (past == null
				|| past.getValue() == null
				|| past.getKey().isBefore(target.minusMinutes(STALE_MINUTES))) {
			return null;
		}

		return signal.value().subtract(past.getValue());
	}

	/**
	 * OVER / NEAR / SENSOR_FAULT: walk 1-minute buckets back from the latest sample while
	 * the bucket value has the same status; a gap longer than STALE_MINUTES
	 * ends the run. A run reaching the 24h lookback returns its first bucket.
	 * STALE: latest sample + STALE_MINUTES (no sample at all -> recordedAt).
	 */
	private LocalDateTime since(Evaluated item, NavigableMap<LocalDateTime, BigDecimal> buckets) {
		Signal signal = item.signal();
		WaterCheckStatus status = item.evaluation().status();

		if (status == WaterCheckStatus.STALE) {
			// no raw in 24h: lastSeenAt (History_Main) + STALE_MINUTES, null if unknown
			if (signal.recordedAt() == null) {
				return signal.lastSeenAt() == null ? null : signal.lastSeenAt().plusMinutes(STALE_MINUTES);
			}

			return signal.value() == null ? signal.recordedAt() : signal.recordedAt().plusMinutes(STALE_MINUTES);
		}

		if (!tracksSince(status)) {
			return null;
		}

		LocalDateTime since = floorMinute(signal.recordedAt());
		LocalDateTime previous = since;

		for (Map.Entry<LocalDateTime, BigDecimal> bucket : buckets.headMap(since, true).descendingMap().entrySet()) {
			if (ChronoUnit.MINUTES.between(bucket.getKey(), previous) > STALE_MINUTES
					|| bucket.getValue() == null
					|| evaluate(bucket.getValue(), item.limits(), item.pressure()).status() != status) {
				break;
			}

			since = bucket.getKey();
			previous = bucket.getKey();
		}

		return since;
	}

	// STOPPED is not an issue
	private boolean isIssue(WaterCheckStatus status) {
		return status == WaterCheckStatus.OVER
				|| status == WaterCheckStatus.NEAR
				|| status == WaterCheckStatus.SENSOR_FAULT
				|| status == WaterCheckStatus.STALE;
	}

	// statuses whose since is traced back through the raw buckets
	private boolean tracksSince(WaterCheckStatus status) {
		return status == WaterCheckStatus.OVER
				|| status == WaterCheckStatus.NEAR
				|| status == WaterCheckStatus.SENSOR_FAULT;
	}

	// unit kg/cm2 / bar, or code containing PRES (any case)
	private boolean isPressure(Signal signal) {
		String unit = signal.unit() == null
				? ""
				: signal.unit().replace(" ", "").toLowerCase(Locale.ROOT);

		return PRESSURE_UNIT_PATTERN.matcher(unit).matches()
				|| (signal.code() != null && signal.code().toUpperCase(Locale.ROOT).contains("PRES"));
	}

	private String reason(WaterCheckStatus status) {
		return switch (status) {
			case STOPPED -> REASON_STOPPED;
			case SENSOR_FAULT -> REASON_SENSOR_FAULT;
			default -> null;
		};
	}

	// ---------------------------------------------------------------------
	// Response building
	// ---------------------------------------------------------------------

	private WaterCheckIssueDto toIssueDto(Issue issue) {
		Signal signal = issue.item().signal();

		return new WaterCheckIssueDto(
				signal.fac(),
				signal.box(),
				signal.plc(),
				signal.name(),
				signal.unit(),
				signal.value(),
				signal.lastValue(),
				formatTs(signal.lastSeenAt()),
				issue.item().evaluation().status(),
				reason(issue.item().evaluation().status()),
				thresholdText(issue.item().limits(), signal.unit()),
				formatTs(issue.since()),
				issue.trend1h()
		);
	}

	private String thresholdText(Limits limits, String unit) {
		if (limits.isEmpty()) {
			return null;
		}

		String text;

		if (limits.low() != null && limits.high() != null) {
			text = plain(limits.low()) + " - " + plain(limits.high());
		} else if (limits.low() != null) {
			text = ">= " + plain(limits.low());
		} else {
			text = "<= " + plain(limits.high());
		}

		return unit == null ? text : text + " " + unit;
	}

	private String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	// code groups first (by code), then no-category groups (by name), then unit
	private Comparator<GroupKey> groupOrder() {
		Comparator<String> text = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);

		return Comparator
				.comparing(GroupKey::code, text)
				.thenComparing(GroupKey::name, text)
				.thenComparing(GroupKey::unit, text);
	}

	// NULL, blank and '0' mean "no category": no code, no threshold lookup
	private String normalizeCateId(String cateId) {
		if (cateId == null || cateId.isBlank() || "0".equals(cateId.trim())) {
			return null;
		}

		return cateId.trim();
	}

	private String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	// same placeholder as /latest
	private String safeText(String value) {
		return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
	}

	private LocalDateTime floorMinute(LocalDateTime value) {
		return value.truncatedTo(ChronoUnit.MINUTES);
	}

	private String formatTs(LocalDateTime value) {
		return value == null
				? null
				: value.atZone(DB_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
