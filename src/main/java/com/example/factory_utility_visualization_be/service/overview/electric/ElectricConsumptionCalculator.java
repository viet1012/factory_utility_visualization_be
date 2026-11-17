package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.BillPeriod;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MeterIssue;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MissingMeter;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MonthPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.UnitPrices;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Values;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Calibrated;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Evn;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Official;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.OfficialValues;
import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.EvnDay;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.LossCalibration;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionMeterDayProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionMeterProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricMeterLastPickProjection;
import com.example.factory_utility_visualization_be.service.overview.electric.HourlyEnergyCache.MeterHour;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.HourMoney;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.HourSum;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.MeterFilter;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Money;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Builds GET /api/utility/electric-consumption from the hourly History_Main
 * energy and the time-of-use tariff of F2_Utility_Cost_Master.
 *
 * Energy source (loadEnergy): the meter-hours of HourlyEnergyCache (cached
 * per month, shared with the dashboards), outliers flagged in memory with
 * the median / P90 of each meter over the request window [first history
 * period, dataUntil) - the same window and rule as the old SQL
 * (findHourlyEnergy / findMeterDays PERCENTILE_CONT), which is only used
 * when no cache is wired (unit tests). Step timings are logged at INFO.
 *
 * Outlier meter-hours (cumulative / catch-up values, see the repo) are not
 * counted: that meter-hour is treated as missing, so the day becomes
 * partial, and the dropped kWh is reported as excludedMwh.
 *
 * The meters in the DB only give hourly kWh, so the Normal / High-peak /
 * Low-peak split of the sheet is rebuilt by putting each hour (or part of an
 * hour) into the band of its tariff time window. Load = devices without any
 * SOLAR channel (the sub-meters measure consumption = grid + solar), solar =
 * SOLAR devices (same split as /power-usage); per scope and hour, grid
 * (bought from EVN) = max(load - solar, 0), and only grid is split into
 * bands / priced at the grid rate. The split uses the CURRENT channel config,
 * so a solar meter that had no SOLAR channel in the past is counted as load
 * for that time. A solar meter belongs to the scope of its DeviceMap fac
 * (SCADA fac or utility.electric-consumption.meter-scope).
 *
 * Coverage of the selected period is measured against the CONFIGURED grid
 * meters of the scope (ElectricConsumptionRepo.findGridMeters), so a meter
 * that stopped reporting stays in the denominator; per day / per period the
 * meters with missing hours are listed (findMeterDays). The monthly history
 * still uses the meters seen in the data (+/- meterWindowDays).
 */
@Slf4j
@Service
public class ElectricConsumptionCalculator {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String ENERGY_NAME = GridSolarSplit.ENERGY_NAME;

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	private static final int OUT_SCALE = 3;

	private static final int HOURS_PER_DAY = 24;

	private final ElectricConsumptionRepo repo;
	private final ElectricityPricingService pricingService;
	private final ElectricConsumptionProperties properties;
	// load / solar / grid per scope x hour + pricing, shared with the dashboards
	private final GridSolarSplit split;
	// F2_Utility_Evn_Daily / F2_Utility_Loss_Calibration; null = none (unit tests)
	private final EvnCalibrationJdbcRepository evnRepo;
	// meter-hours cached per month (shared with the dashboards); null = old
	// SQL path findHourlyEnergy / findMeterDays (unit tests)
	private final HourlyEnergyCache hourlyCache;

	// same 3 dependencies as before (GridSolarSplit is built from them); no
	// EVN / calibration, SQL path
	public ElectricConsumptionCalculator(
			ElectricConsumptionRepo repo,
			ElectricityPricingService pricingService,
			ElectricConsumptionProperties properties
	) {
		this(repo, pricingService, properties, null, null);
	}

	@Autowired
	public ElectricConsumptionCalculator(
			ElectricConsumptionRepo repo,
			ElectricityPricingService pricingService,
			ElectricConsumptionProperties properties,
			EvnCalibrationJdbcRepository evnRepo,
			HourlyEnergyCache hourlyCache
	) {
		this.repo = repo;
		this.pricingService = pricingService;
		this.properties = properties;
		this.split = new GridSolarSplit(repo, pricingService, properties);
		this.evnRepo = evnRepo;
		this.hourlyCache = hourlyCache;
	}

	/**
	 * Named step durations of one request, for the INFO log line. step()
	 * charges the time since the previous mark to a name; add() charges a
	 * slice measured inside a loop (e.g. meterIssues, also part of the
	 * enclosing step).
	 */
	static final class StepTimer {
		private final long started = System.nanoTime();
		private long mark = started;
		private final Map<String, Long> nanos = new LinkedHashMap<>();

		void step(String name) {
			long now = System.nanoTime();
			add(name, now - mark);
			mark = now;
		}

		void add(String name, long ns) {
			nanos.merge(name, ns, Long::sum);
		}

		String summary() {
			StringBuilder sb = new StringBuilder();
			nanos.forEach((name, ns) -> sb.append(name).append(' ').append(ns / 1_000_000).append(" ms, "));
			return sb.append("total ").append((System.nanoTime() - started) / 1_000_000).append(" ms").toString();
		}
	}

	/**
	 * Energy of one window [from, to): load / solar per scope x hour (outliers
	 * flagged) and the configured grid meters per scope with their valid /
	 * outlier hours per day from dataFrom.
	 */
	private record Energy(
			Map<ScopeDef, Map<LocalDateTime, HourSum>> hours,
			Map<ScopeDef, List<MeterData>> meters,
			int meterHours
	) {}

	// per meter outlier stats over the window (median: grid, P90: SOLAR)
	private record MeterStats(BigDecimal median, BigDecimal p90) {}

	// the 3 charts of the sheet: TTL KVH, Fac A&B (Trans No.2), Fac C (Trans No.1)
	enum ScopeDef {
		KVH("TTL KVH: A&B + C", Set.of()),
		// FAC_AB = meters moved to Trans No.2 by meter-scope.fac-ab
		FAC_AB("Fac A&B", Set.of("FAC_A", "FAC_B", "FAC_AB")),
		FAC_C("Fac C", Set.of("FAC_C"));

		private final String displayName;
		// empty = every fac
		private final Set<String> facs;

		ScopeDef(String displayName, Set<String> facs) {
			this.displayName = displayName;
			this.facs = facs;
		}

		boolean includes(String facUpper) {
			return facs.isEmpty() || facs.contains(facUpper);
		}
	}

	// [from, to) dates of one bill period
	public record Period(YearMonth month, LocalDate from, LocalDate to) {
		public int days() {
			return (int) ChronoUnit.DAYS.between(from, to);
		}
	}

	/**
	 * Measured grid of one scope x day (POST /loss-calibration/recompute).
	 *
	 * @param fullCoverage day is over and EVERY configured grid meter of the
	 *                     scope has 24 valid (non-outlier) hours
	 */
	public record MeasuredDay(
			LocalDate day,
			// sum over hours of max(load - solar, 0)
			BigDecimal gridKwh,
			// sum of the grid sub-meters (consumption = grid + solar)
			BigDecimal loadKwh,
			// configured grid meters of the scope
			int meters,
			// meter-hours with valid grid data
			int meterHours,
			boolean fullCoverage
	) {
		// meterHours / (meters x 24) in %, null = no configured meter
		public BigDecimal coveragePct() {
			return meters == 0 ? null : BigDecimal.valueOf(meterHours * 100L)
					.divide(BigDecimal.valueOf((long) meters * HOURS_PER_DAY), 1, RoundingMode.HALF_UP);
		}
	}

	/**
	 * Loss calibration rows; a day uses the row with the highest id (= most
	 * recent recompute) of its scope whose [effective_from, effective_to]
	 * contains it, none = no calibrated values that day.
	 */
	private static final class CalibrationTable {
		private static final CalibrationTable EMPTY = new CalibrationTable(List.of());

		// newest id first
		private final List<LossCalibration> rows;
		private final Map<String, Optional<LossCalibration>> byScopeDay = new HashMap<>();

		private CalibrationTable(List<LossCalibration> rows) {
			this.rows = rows.stream().sorted(Comparator.comparingLong(LossCalibration::id).reversed()).toList();
		}

		private LossCalibration find(String scope, LocalDate day) {
			return byScopeDay.computeIfAbsent(scope + "|" + day, key -> rows.stream()
					.filter(row -> scope.equals(row.scope()) && row.covers(day))
					.findFirst()).orElse(null);
		}
	}

	/**
	 * kWh / USD of one scope on one day (or summed over several days).
	 *
	 * The sub-meters measure the LOAD (grid + solar consumed), not the energy
	 * bought from EVN, so per hour and scope: grid = max(load - solar, 0).
	 * Bands / grid money are computed from that grid kWh (see toAcc); grid
	 * and load of several hours / days are plain sums of the hourly values.
	 */
	private static final class Acc {
		// grid kWh / USD per tariff band
		private final Map<TouBand, BigDecimal> bandKwh = new EnumMap<>(TouBand.class);
		private final Map<TouBand, BigDecimal> bandUsd = new EnumMap<>(TouBand.class);
		// sum of the grid sub-meters (= consumption)
		private BigDecimal load = BigDecimal.ZERO;
		// bought from the grid: sum over hours of max(load - solar, 0)
		private BigDecimal grid = BigDecimal.ZERO;
		private BigDecimal solar = BigDecimal.ZERO;
		private BigDecimal solarUsd = BigDecimal.ZERO;
		// some grid (load) meter reported
		private boolean hasLoad;
		private boolean hasSolar;
		// grid meters reporting in hour h of the day (meaningful for one day only)
		private final int[] gridMeters = new int[HOURS_PER_DAY];
		// kWh dropped as outliers
		private BigDecimal excluded = BigDecimal.ZERO;
		// calibrated grid = measured grid + loss per hour, only hours with grid
		// (load) data; meaningful for one day only (DayPoint.calibrated)
		private final Map<TouBand, BigDecimal> calBandKwh = new EnumMap<>(TouBand.class);
		private BigDecimal calGrid = BigDecimal.ZERO;
		private int calHours;
		// kWh added per hour with data (KVH: FAC_AB + FAC_C) and the
		// F2_Utility_Loss_Calibration ids used; null = not calibrated
		private BigDecimal calLossPerHour;
		private List<Long> calIds;
		// official day built from F2_Utility_Evn_Daily (one day only, not merged by add)
		private boolean fromEvn;

		private Acc() {
			for (TouBand band : TouBand.values()) {
				bandKwh.put(band, BigDecimal.ZERO);
				bandUsd.put(band, BigDecimal.ZERO);
				calBandKwh.put(band, BigDecimal.ZERO);
			}
		}

		private void add(Acc other) {
			other.bandKwh.forEach((band, value) -> bandKwh.merge(band, value, BigDecimal::add));
			other.bandUsd.forEach((band, value) -> bandUsd.merge(band, value, BigDecimal::add));
			other.calBandKwh.forEach((band, value) -> calBandKwh.merge(band, value, BigDecimal::add));
			calGrid = calGrid.add(other.calGrid);
			calHours += other.calHours;
			if (calIds == null) {
				calIds = other.calIds;
				calLossPerHour = other.calLossPerHour;
			}
			load = load.add(other.load);
			grid = grid.add(other.grid);
			solar = solar.add(other.solar);
			solarUsd = solarUsd.add(other.solarUsd);
			hasLoad |= other.hasLoad;
			hasSolar |= other.hasSolar;
			excluded = excluded.add(other.excluded);

			for (int h = 0; h < HOURS_PER_DAY; h++) {
				gridMeters[h] += other.gridMeters[h];
			}
		}

		private boolean hasData() {
			return hasLoad || hasSolar;
		}

		private int hoursWithData() {
			return (int) Arrays.stream(gridMeters).filter(count -> count > 0).count();
		}

		private int meterHours() {
			return Arrays.stream(gridMeters).sum();
		}

		private int maxMeters() {
			return Arrays.stream(gridMeters).max().orElse(0);
		}

		private BigDecimal gridUsd() {
			return bandUsd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		// average grid USD per kWh over the priced hours; null = nothing priced
		private BigDecimal gridUsdPerKwh() {
			BigDecimal kwh = bandKwh.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
			return kwh.signum() <= 0 ? null : gridUsd().divide(kwh, 8, RoundingMode.HALF_UP);
		}
	}

	/**
	 * Official values summed over a set of days: an EVN day is always in the
	 * set (never left out as partial / without measured data), a MEASURED day
	 * follows the measured rule (total: has data, average: complete). Its own
	 * day count is the average divisor and its own priced days decide money,
	 * since the set differs from the measured one.
	 */
	private static final class OfficialSum {
		private final Acc acc = new Acc();
		private int evnDays;
		private int measuredDays;
		private int pricedDays;

		private void add(Acc day, boolean dayPriced) {
			acc.add(day);

			if (day.fromEvn) {
				evnDays++;
			} else {
				measuredDays++;
			}

			if (dayPriced) {
				pricedDays++;
			}
		}

		private int days() {
			return evnDays + measuredDays;
		}

		// sum (average = false) or average per day; null = no day
		private Values mwh(ElectricConsumptionCalculator calc, boolean average, boolean priced) {
			return days() == 0 ? null : calc.mwh(acc, divisor(average), priced && pricedDays > 0);
		}

		private Values kusd(ElectricConsumptionCalculator calc, boolean average, boolean priced) {
			return days() == 0 ? null : calc.kusd(acc, divisor(average), priced && pricedDays > 0);
		}

		private BigDecimal divisor(boolean average) {
			return average ? BigDecimal.valueOf(days()) : BigDecimal.ONE;
		}
	}

	// configured grid meter with its valid / outlier hours per day of the selected period
	private static final class MeterData {
		private final String meter;
		private final String fac;
		// day -> {valid hours, outlier hours}
		private final Map<LocalDate, int[]> hours = new HashMap<>();
		// end of the last hour with valid data (hourOffset applied), null = never
		private LocalDateTime lastDataAt;
		// valid kWh / valid hours over the selected period (estimate of missing hours)
		private BigDecimal validKwh = BigDecimal.ZERO;
		private int validHoursTotal;

		private MeterData(String meter, String fac) {
			this.meter = meter;
			this.fac = fac;
		}

		private int validHours(LocalDate day) {
			int[] h = hours.get(day);
			return h == null ? 0 : h[0];
		}
	}

	private static final String NO_DATA = "NO_DATA";
	private static final String PARTIAL = "PARTIAL";
	private static final String OUTLIER = "OUTLIER";

	// MeterIssue.severity
	private static final String MAJOR = "MAJOR";
	private static final String MINOR = "MINOR";

	private static final Comparator<MissingMeter> MISSING_ORDER = Comparator
			.comparing((MissingMeter m) -> !NO_DATA.equals(m.reason()))
			.thenComparingInt(MissingMeter::validHours)
			.thenComparing(MissingMeter::meter);

	// MAJOR first, then most missing hours, then name
	private static final Comparator<MeterIssue> ISSUE_ORDER = Comparator
			.comparing((MeterIssue m) -> !MAJOR.equals(m.severity()))
			.thenComparing(Comparator.comparingInt(MeterIssue::missingHours).reversed())
			.thenComparing(MeterIssue::meter);

	public Period period(YearMonth month) {
		int cutoff = properties.getBillingCutoffDay();
		return new Period(month, month.minusMonths(1).atDay(cutoff), month.atDay(cutoff));
	}

	// bill month that contains the given day
	public YearMonth billMonthOf(LocalDate day) {
		YearMonth month = YearMonth.from(day);
		return day.getDayOfMonth() >= properties.getBillingCutoffDay() ? month.plusMonths(1) : month;
	}

	// utility.electricity.hour-offset
	public int defaultHourOffset() {
		return pricingService.hourOffset();
	}

	public ElectricConsumptionResponseDto calculate(YearMonth month, int months) {
		return calculate(month, months, defaultHourOffset());
	}

	@Transactional(readOnly = true)
	public ElectricConsumptionResponseDto calculate(YearMonth month, int months, int hourOffset) {
		return calculate(month, months, hourOffset, true);
	}

	/**
	 * @param withEvn false = measured only (view=measured): F2_Utility_Evn_Daily
	 *                / F2_Utility_Loss_Calibration are not read, so calibrated /
	 *                evn are null and official = the measured values
	 */
	@Transactional(readOnly = true)
	public ElectricConsumptionResponseDto calculate(YearMonth month, int months, int hourOffset, boolean withEvn) {
		LocalDateTime lastFullHour = LocalDateTime.now(APP_ZONE).truncatedTo(ChronoUnit.HOURS);

		Period current = period(month);
		List<Period> history = new ArrayList<>(months);

		for (int i = months; i >= 1; i--) {
			history.add(period(month.minusMonths(i)));
		}

		LocalDateTime queryFrom = (history.isEmpty() ? current : history.get(0)).from().atStartOfDay();
		LocalDate historyStart = properties.getHistoryStartDate();

		// nothing before historyStartDate is loaded, so those days / periods are null
		if (historyStart != null && queryFrom.isBefore(historyStart.atStartOfDay())) {
			queryFrom = historyStart.atStartOfDay();
		}
		LocalDateTime currentEnd = current.to().atStartOfDay();
		LocalDateTime dataUntil = currentEnd.isAfter(lastFullHour) ? lastFullHour : currentEnd;

		// first hour of the selected period that is loaded (historyStartDate may cut it)
		LocalDateTime currentFrom = current.from().atStartOfDay();
		LocalDateTime dataFrom = currentFrom.isBefore(queryFrom) ? queryFrom : currentFrom;

		StepTimer timer = new StepTimer();
		MeterFilter filter = meterFilter();
		Optional<Pricing> pricing = pricingService.load();
		timer.step("filter+tariff");

		// one window [queryFrom, dataUntil) for the hourly sums AND the meter
		// days, so both see the same outliers (median over the whole window)
		Energy energy = loadEnergy(queryFrom, dataFrom, dataUntil, hourOffset, filter, timer);
		CalibrationTable calibrations = withEvn ? loadCalibrations() : CalibrationTable.EMPTY;
		timer.step("calibrations");
		Map<ScopeDef, Map<LocalDate, Acc>> days = loadDays(energy.hours(), pricing, calibrations);
		timer.step("days+pricing");

		// history periods too: monthly[].official
		Map<String, Map<LocalDate, EvnDay>> evn = !withEvn ? Map.of() : loadEvn(
				(history.isEmpty() ? current : history.get(0)).from(), current.to());
		timer.step("EVN");

		Map<ScopeDef, Map<LocalDate, Acc>> official = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			// KVH: its own EVN row (not FAC_AB + FAC_C)
			official.put(def, officialDays(days.get(def), evn.getOrDefault(def.name(), Map.of()), pricing));
		}
		timer.step("official");

		List<Scope> scopes = new ArrayList<>(ScopeDef.values().length);

		for (ScopeDef def : ScopeDef.values()) {
			scopes.add(scope(def, days.get(def), energy.meters().get(def), history, current, dataFrom, dataUntil,
					pricing.isPresent(),
					day -> pricing.isPresent() && pricing.get().priced(day),
					evn.getOrDefault(def.name(), Map.of()),
					official.get(def),
					timer));
		}
		// includes meterIssues (also logged on its own)
		timer.step("scopes");

		log.info("electric-consumption {} months {} offset {} [{} , {}): {} meter-hours ({}), {}",
				month, months, hourOffset, queryFrom, dataUntil, energy.meterHours(),
				hourlyCache == null ? "SQL" : "hourly cache", timer.summary());

		return new ElectricConsumptionResponseDto(
				month.toString(),
				new BillPeriod(label(current), current.from().toString(), current.to().toString(), current.days()),
				dataUntil.isAfter(current.from().atStartOfDay()) ? formatTs(dataUntil) : null,
				pricing.isPresent(),
				properties.getMinCoveragePct(),
				hourOffset,
				historyStart == null ? null : historyStart.toString(),
				pricingService.excludedMeterKeys().stream().filter(key -> !key.isEmpty()).toList(),
				// version of the last hour with data (dataUntil is exclusive)
				unitPrices(pricing, dataUntil.minusHours(1).toLocalDate()),
				scopes
		);
	}

	// ---------------------------------------------------------------------
	// Data loading
	// ---------------------------------------------------------------------

	private MeterFilter meterFilter() {
		return split.meterFilter();
	}

	/**
	 * Energy of the window [from, to) (hour start, hourOffset applied) and the
	 * meter days from dataFrom:
	 * - HourlyEnergyCache (default): meter-hours of the cached months, median /
	 *   P90 per meter over EXACTLY this window in memory (= the PERCENTILE_CONT
	 *   OVER (PARTITION BY box_device_id) of the SQL), outlier flag
	 *   GridSolarSplit.isOutlier, then the hourly sums AND the meter days from
	 *   the same flagged rows: no findHourlyEnergy / findMeterDays any more
	 * - SQL (hourlyCache null, unit tests): findHourlyEnergy + findMeterDays
	 */
	private Energy loadEnergy(
			LocalDateTime from,
			LocalDateTime dataFrom,
			LocalDateTime to,
			int offset,
			MeterFilter filter,
			StepTimer timer
	) {
		if (hourlyCache != null) {
			return cachedEnergy(from, dataFrom, to, offset, filter, timer);
		}

		Map<ScopeDef, Map<LocalDateTime, HourSum>> hours = split.hourSums(
				from, to, offset, filter, List.of(ScopeDef.values()), ScopeDef::includes);
		timer.step("hourly query (SQL)");
		Map<ScopeDef, List<MeterData>> meters = loadMeters(from, dataFrom, to, offset, filter);
		timer.step("meter days query (SQL)");

		return new Energy(hours, meters, -1);
	}

	private Energy cachedEnergy(
			LocalDateTime from,
			LocalDateTime dataFrom,
			LocalDateTime to,
			int offset,
			MeterFilter filter,
			StepTimer timer
	) {
		Map<String, MeterData> gridMeters = gridMeters(filter);
		Map<ScopeDef, Map<LocalDateTime, HourSum>> hours = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			hours.put(def, new TreeMap<>());
		}

		timer.step("grid meters");

		// 1) meter-hours with hourAt + offset in [from, to) (same as the SQL
		// window pick_at in [from + 1h - offset, to + 1h - offset))
		List<MeterHour> rows = hourlyCache.rows(from.minusHours(offset), to.minusHours(offset));
		timer.step("hourly rows (cache)");

		// 2) median (grid) / P90 (SOLAR) kWh per hour of each meter over the window
		Map<String, List<BigDecimal>> byMeter = new HashMap<>();
		Map<String, Boolean> solarMeter = new HashMap<>();

		for (MeterHour row : rows) {
			byMeter.computeIfAbsent(row.meter(), key -> new ArrayList<>()).add(row.kwh());
			solarMeter.putIfAbsent(row.meter(), row.solar());
		}

		Map<String, MeterStats> stats = new HashMap<>(byMeter.size() * 2);

		byMeter.forEach((meter, values) -> {
			Collections.sort(values);
			boolean solar = solarMeter.get(meter);
			stats.put(meter, new MeterStats(
					solar ? null : GridSolarSplit.percentile(values, 0.5),
					solar ? GridSolarSplit.percentile(values, 0.9) : null));
		});
		timer.step("median/outlier");

		// 3) flag + hourly sums per scope + grid meter days (from dataFrom)
		boolean meterDays = dataFrom.isBefore(to);
		// last pick_at of a non-outlier hour in the window (findMeterDays MeterLast.lv)
		Map<String, LocalDateTime> lastValidPick = new HashMap<>();

		for (MeterHour row : rows) {
			MeterStats s = stats.get(row.meter());
			boolean outlier = GridSolarSplit.isOutlier(row.kwh(), row.solar(), s.median(), s.p90(), properties);
			LocalDateTime hourAt = row.hourAt().plusHours(offset);

			for (ScopeDef def : ScopeDef.values()) {
				if (def.includes(row.fac())) {
					hours.get(def).computeIfAbsent(hourAt, key -> new HourSum()).add(row.kwh(), row.solar(), outlier, 1);
				}
			}

			MeterData meter = row.solar() || !meterDays ? null : gridMeters.get(row.meter());

			if (meter == null) {
				continue;
			}

			if (!outlier && row.lastPickAt() != null) {
				lastValidPick.merge(row.meter(), row.lastPickAt(), (a, b) -> a.isAfter(b) ? a : b);
			}

			if (!hourAt.isBefore(dataFrom)) {
				int[] h = meter.hours.computeIfAbsent(hourAt.toLocalDate(), key -> new int[2]);

				if (outlier) {
					h[1]++;
				} else {
					h[0]++;
					meter.validHoursTotal++;
					meter.validKwh = meter.validKwh.add(row.kwh());
				}
			}
		}
		timer.step("aggregate");

		if (meterDays) {
			// meters without a valid hour in the window: last pick_at before it
			List<String> missing = gridMeters.keySet().stream()
					.filter(meter -> !lastValidPick.containsKey(meter))
					.map(meter -> meter.toUpperCase(Locale.ROOT))
					.toList();

			if (!missing.isEmpty()) {
				for (ElectricMeterLastPickProjection row : repo.findGridMeterLastPickBefore(
						ENERGY_NAME, from.plusHours(1L - offset), missing,
						filter.excluded(), filter.facAb(), filter.facC())) {
					if (row.getMeter() != null && row.getLastPickAt() != null) {
						lastValidPick.putIfAbsent(row.getMeter().trim(), row.getLastPickAt());
					}
				}
			}

			// pick_at is stamped at the end of its hour -> end of the last valid hour
			lastValidPick.forEach((meter, pickAt) -> {
				MeterData data = gridMeters.get(meter);

				if (data != null) {
					data.lastDataAt = pickAt.truncatedTo(ChronoUnit.HOURS).plusHours(offset);
				}
			});
			timer.step("meter last pick");
		}

		return new Energy(hours, byScope(gridMeters), rows.size());
	}

	// configured grid meters (findGridMeters), box_device_id trimmed -> meter
	private Map<String, MeterData> gridMeters(MeterFilter filter) {
		Map<String, MeterData> byMeter = new LinkedHashMap<>();

		for (ElectricConsumptionMeterProjection row : repo.findGridMeters(
				ENERGY_NAME, filter.excluded(), filter.facAb(), filter.facC())) {
			if (row.getMeter() != null) {
				byMeter.putIfAbsent(row.getMeter().trim(), new MeterData(row.getMeter().trim(), facKey(row.getFac())));
			}
		}

		return byMeter;
	}

	private static Map<ScopeDef, List<MeterData>> byScope(Map<String, MeterData> byMeter) {
		Map<ScopeDef, List<MeterData>> meters = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			meters.put(def, byMeter.values().stream().filter(m -> def.includes(m.fac)).toList());
		}

		return meters;
	}

	/**
	 * SQL path (unit tests): configured grid meters per scope, with their
	 * valid / outlier hours per day of [dataFrom, dataUntil). The outlier
	 * window is [from, dataUntil), the same as findHourlyEnergy, so both see
	 * the same outliers.
	 */
	private Map<ScopeDef, List<MeterData>> loadMeters(
			LocalDateTime from,
			LocalDateTime dataFrom,
			LocalDateTime dataUntil,
			int offset,
			MeterFilter filter
	) {
		Map<String, MeterData> byMeter = gridMeters(filter);

		if (dataFrom.isBefore(dataUntil)) {
			List<ElectricConsumptionMeterDayProjection> rows = repo.findMeterDays(
					ENERGY_NAME, from.plusHours(1L - offset), dataUntil.plusHours(1L - offset), dataFrom, offset,
					properties.getOutlierRatio(), properties.getOutlierMinKwh(),
					filter.excluded(), filter.facAb(), filter.facC());

			for (ElectricConsumptionMeterDayProjection row : rows) {
				if (row.getMeter() == null) {
					continue;
				}

				String meter = row.getMeter().trim();
				MeterData data = byMeter.computeIfAbsent(meter, key -> new MeterData(key, facKey(row.getFac())));

				// pick_at is stamped at the end of its hour -> end of the last valid hour
				if (row.getLastPickAt() != null) {
					data.lastDataAt = row.getLastPickAt().truncatedTo(ChronoUnit.HOURS).plusHours(offset);
				}

				if (row.getDayAt() != null) {
					int valid = row.getValidHours() == null ? 0 : row.getValidHours();
					data.hours.put(row.getDayAt().toLocalDate(), new int[] {
							valid,
							row.getOutlierHours() == null ? 0 : row.getOutlierHours()
					});
					data.validHoursTotal += valid;
					if (row.getValidKwh() != null) {
						data.validKwh = data.validKwh.add(row.getValidKwh());
					}
				}
			}
		}

		return byScope(byMeter);
	}

	private static String facKey(String fac) {
		return GridSolarSplit.facKey(fac);
	}

	// load / solar per scope x hour (loadEnergy) -> days with grid / bands / money
	private Map<ScopeDef, Map<LocalDate, Acc>> loadDays(
			Map<ScopeDef, Map<LocalDateTime, HourSum>> hours,
			Optional<Pricing> pricing,
			CalibrationTable calibrations
	) {
		Map<ScopeDef, Map<LocalDate, Acc>> days = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			days.put(def, new HashMap<>());
		}

		// grid = max(load - solar, 0) per scope x hour, then bands / money
		for (ScopeDef def : ScopeDef.values()) {
			Map<LocalDate, Acc> scopeDays = days.get(def);

			hours.get(def).forEach((hourAt, sum) -> {
				Acc acc = toAcc(sum, hourAt, pricing);

				if (def == ScopeDef.KVH) {
					calibrateKvh(acc, hours, hourAt, calibrations, pricing);
				} else {
					LossCalibration calibration = calibrations.find(def.name(), hourAt.toLocalDate());

					// only hours with grid (load) data get the loss: missing meters / hours are not filled
					if (calibration != null && sum.hasLoad()) {
						calibrate(acc, List.of(calibration.id()), calibration.lossKwhPerHour(),
								sum.grid().add(calibration.lossKwhPerHour()), hourAt, pricing);
					}
				}

				scopeDays.computeIfAbsent(hourAt.toLocalDate(), key -> new Acc()).add(acc);
			});
		}

		return days;
	}

	/**
	 * KVH is never calibrated on its own: per hour, calibrated KVH =
	 * calibrated FAC_AB + calibrated FAC_C, each = fac grid + fac loss when that
	 * fac has grid data in the hour. Needs a calibration of BOTH facs on the
	 * day, else KVH has no calibrated value (a half-calibrated KVH would not
	 * equal FAC_AB + FAC_C). KVH rows of F2_Utility_Loss_Calibration are ignored.
	 */
	private static void calibrateKvh(
			Acc acc,
			Map<ScopeDef, Map<LocalDateTime, HourSum>> hours,
			LocalDateTime hourAt,
			CalibrationTable calibrations,
			Optional<Pricing> pricing
	) {
		LocalDate day = hourAt.toLocalDate();
		LossCalibration ab = calibrations.find(ScopeDef.FAC_AB.name(), day);
		LossCalibration c = calibrations.find(ScopeDef.FAC_C.name(), day);

		if (ab == null || c == null) {
			return;
		}

		BigDecimal grid = BigDecimal.ZERO;
		boolean any = false;

		for (Map.Entry<ScopeDef, LossCalibration> fac : Map.of(ScopeDef.FAC_AB, ab, ScopeDef.FAC_C, c).entrySet()) {
			HourSum sum = hours.get(fac.getKey()).get(hourAt);

			if (sum != null && sum.hasLoad()) {
				grid = grid.add(sum.grid()).add(fac.getValue().lossKwhPerHour());
				any = true;
			}
		}

		if (any) {
			calibrate(acc, List.of(ab.id(), c.id()), ab.lossKwhPerHour().add(c.lossKwhPerHour()), grid, hourAt,
					pricing);
		}
	}

	// calibrated grid of one scope x hour (measured grid + loss), same band split as the grid
	private static void calibrate(
			Acc acc,
			List<Long> calibrationIds,
			BigDecimal lossPerHour,
			BigDecimal grid,
			LocalDateTime hourAt,
			Optional<Pricing> pricing
	) {
		acc.calGrid = grid;
		acc.calHours = 1;
		acc.calIds = calibrationIds;
		acc.calLossPerHour = lossPerHour;

		if (pricing.isPresent()) {
			for (TouShare share : pricing.get().shares(hourAt)) {
				acc.calBandKwh.merge(share.band(), grid.multiply(share.fraction()), BigDecimal::add);
			}
		}
	}

	// every calibration row; table missing / unreadable -> none (GET must keep working)
	private CalibrationTable loadCalibrations() {
		if (evnRepo == null) {
			return CalibrationTable.EMPTY;
		}

		try {
			return new CalibrationTable(evnRepo.findCalibrations());
		} catch (DataAccessException e) {
			log.warn("F2_Utility_Loss_Calibration not readable, no calibrated values: {}", e.getMessage());
			return CalibrationTable.EMPTY;
		}
	}

	// scope -> day -> EVN row of [from, to); table missing -> none
	private Map<String, Map<LocalDate, EvnDay>> loadEvn(LocalDate from, LocalDate to) {
		if (evnRepo == null) {
			return Map.of();
		}

		try {
			Map<String, Map<LocalDate, EvnDay>> evn = new HashMap<>();

			for (EvnDay row : evnRepo.findEvnDays(from, to, null)) {
				evn.computeIfAbsent(row.scope(), key -> new HashMap<>()).put(row.day(), row);
			}

			return evn;
		} catch (DataAccessException e) {
			log.warn("F2_Utility_Evn_Daily not readable, no evn values: {}", e.getMessage());
			return Map.of();
		}
	}

	/**
	 * Official kWh / USD of one scope per day: the EVN row of the day when
	 * imported (evnAcc), else the measured day itself (same Acc, so the
	 * MEASURED values are an exact copy of mwh / kusd). Never calibrated.
	 */
	private static Map<LocalDate, Acc> officialDays(
			Map<LocalDate, Acc> measured,
			Map<LocalDate, EvnDay> evn,
			Optional<Pricing> pricing
	) {
		Map<LocalDate, Acc> official = new HashMap<>(measured);
		evn.forEach((day, row) -> official.put(day, evnAcc(row, measured.get(day), pricing)));
		return official;
	}

	/**
	 * One EVN day: grid / bands = EVN kWh, band USD = band kWh x grid price of
	 * the band on that day (same tariff and factor as the measured grid);
	 * solar kWh / USD = measured; load = grid + solar. Bands / USD stay 0 when
	 * the day has no tariff version (same as a measured day, money null).
	 */
	private static Acc evnAcc(EvnDay row, Acc measured, Optional<Pricing> pricing) {
		Acc acc = new Acc();

		acc.fromEvn = true;
		acc.hasLoad = true;
		acc.grid = row.totalKwh();

		if (measured != null && measured.hasSolar) {
			acc.hasSolar = true;
			acc.solar = measured.solar;
			acc.solarUsd = measured.solarUsd;
		}

		acc.load = acc.grid.add(acc.solar);

		if (pricing.isPresent() && pricing.get().priced(row.day())) {
			// shared EVN pricing (also used by the dashboards)
			Map<TouBand, Money> money = OfficialEnergyService.bandMoney(row, pricing.get());

			OfficialEnergyService.bandKwh(row).forEach((band, kwh) -> {
				acc.bandKwh.put(band, kwh);

				Money m = money.get(band);

				if (m != null) {
					acc.bandUsd.put(band, m.usd());
				}
			});
		}

		return acc;
	}

	/**
	 * Measured grid per day of [from, to) for one scope (KVH | FAC_AB | FAC_C)
	 * with the same rules as the daily chart (outliers dropped, grid =
	 * max(load - solar, 0) per hour), for the loss calibration. Only days with
	 * grid data; days not over yet are never fullCoverage. Outliers are judged
	 * against the meter's median over [from, to) (GET uses its own window, so
	 * a borderline meter-hour can differ).
	 */
	@Transactional(readOnly = true)
	public Map<LocalDate, MeasuredDay> measuredDays(String scope, LocalDate from, LocalDate to, int hourOffset) {
		ScopeDef def = ScopeDef.valueOf(scope);
		LocalDateTime lastFullHour = LocalDateTime.now(APP_ZONE).truncatedTo(ChronoUnit.HOURS);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime end = to.atStartOfDay().isAfter(lastFullHour) ? lastFullHour : to.atStartOfDay();
		LocalDate historyStart = properties.getHistoryStartDate();

		if (historyStart != null && start.isBefore(historyStart.atStartOfDay())) {
			start = historyStart.atStartOfDay();
		}

		Map<LocalDate, MeasuredDay> result = new java.util.TreeMap<>();

		if (!start.isBefore(end)) {
			return result;
		}

		MeterFilter filter = meterFilter();
		StepTimer timer = new StepTimer();
		Energy energy = loadEnergy(start, start, end, hourOffset, filter, timer);
		Map<LocalDate, Acc> days = loadDays(energy.hours(), Optional.empty(), CalibrationTable.EMPTY).get(def);
		List<MeterData> meters = energy.meters().get(def);
		LocalDateTime until = end;
		log.debug("measured days {} [{} , {}): {}", scope, start, end, timer.summary());

		days.forEach((day, acc) -> {
			if (!acc.hasLoad) {
				return;
			}

			boolean over = !day.plusDays(1).atStartOfDay().isAfter(until);
			boolean everyMeterFull = !meters.isEmpty()
					&& meters.stream().allMatch(m -> m.validHours(day) >= HOURS_PER_DAY);

			result.put(day, new MeasuredDay(day, acc.grid, acc.load, meters.size(), acc.meterHours(),
					over && everyMeterFull));
		});

		return result;
	}

	// one scope x hour: grid = max(load - solar, 0) split into tariff bands
	private Acc toAcc(HourSum sum, LocalDateTime hourAt, Optional<Pricing> pricing) {
		Acc acc = new Acc();

		acc.load = sum.load();
		acc.grid = sum.grid();
		acc.solar = sum.solar();
		acc.hasLoad = sum.hasLoad();
		acc.hasSolar = sum.hasSolar();
		acc.excluded = sum.excluded();
		acc.gridMeters[hourAt.getHour()] = sum.gridMeters();

		HourMoney money = GridSolarSplit.price(sum, hourAt, pricing);
		acc.bandKwh.putAll(money.bandKwh());
		acc.bandUsd.putAll(money.bandUsd());
		acc.solarUsd = money.solarUsd();

		return acc;
	}

	// ---------------------------------------------------------------------
	// Sections
	// ---------------------------------------------------------------------

	private Scope scope(
			ScopeDef def,
			Map<LocalDate, Acc> days,
			List<MeterData> gridMeters,
			List<Period> history,
			Period current,
			LocalDateTime dataFrom,
			LocalDateTime dataUntil,
			boolean priced,
			// a tariff version is in effect on that day (else daily bands / K$ = null)
			Predicate<LocalDate> pricedOn,
			// F2_Utility_Evn_Daily rows of the scope (history + selected period)
			Map<LocalDate, EvnDay> evn,
			// officialDays(): EVN day when imported, else the measured day
			Map<LocalDate, Acc> official,
			StepTimer timer
	) {
		List<MonthPoint> monthly = new ArrayList<>(history.size());

		for (Period period : history) {
			Acc complete = new Acc();
			// official average: every EVN day + complete MEASURED days
			OfficialSum officialAverage = new OfficialSum();
			int meters = meters(days, period);
			int daysWithData = 0;
			int completeDays = 0;
			int pricedCompleteDays = 0;
			BigDecimal excluded = BigDecimal.ZERO;

			for (LocalDate day = period.from(); day.isBefore(period.to()); day = day.plusDays(1)) {
				Acc acc = days.get(day);

				if (acc != null) {
					excluded = excluded.add(acc.excluded);
				}

				// an EVN day always counts as complete, measured data or not
				Acc dayOfficial = official.get(day);
				boolean evnDay = dayOfficial != null && dayOfficial.fromEvn;

				if (evnDay) {
					officialAverage.add(dayOfficial, pricedOn.test(day));
				}

				if (acc == null || !acc.hasData()) {
					continue;
				}

				daysWithData++;

				// history: expected meters still from the data (unchanged)
				if (isComplete(day, acc, dataUntil, (long) expectedMeters(days, day) * HOURS_PER_DAY)) {
					complete.add(acc);
					completeDays++;

					if (!evnDay) {
						officialAverage.add(acc, pricedOn.test(day));
					}

					if (pricedOn.test(day)) {
						pricedCompleteDays++;
					}
				}
			}

			BigDecimal divisor = BigDecimal.valueOf(Math.max(completeDays, 1));
			// money only when at least one averaged day has a tariff version
			boolean monthPriced = priced && pricedCompleteDays > 0;

			monthly.add(new MonthPoint(
					period.month().toString(),
					label(period),
					period.from().toString(),
					period.to().toString(),
					period.days(),
					meters,
					daysWithData,
					completeDays,
					completeDays < period.days(),
					excludedMwh(excluded),
					completeDays == 0 ? null : mwh(complete, divisor, monthPriced),
					completeDays == 0 ? null : kusd(complete, divisor, monthPriced),
					pricedCompleteDays < completeDays,
					officialAverage.days() == 0 ? null : new OfficialValues(
							officialAverage.mwh(this, true, priced),
							officialAverage.kusd(this, true, priced)),
					officialAverage.evnDays,
					officialAverage.measuredDays
			));
		}

		List<DayPoint> daily = new ArrayList<>(current.days());
		Acc total = new Acc();
		Acc complete = new Acc();
		// official: total = every EVN day + MEASURED days with data,
		// average = every EVN day + complete MEASURED days
		OfficialSum officialTotal = new OfficialSum();
		OfficialSum officialAverage = new OfficialSum();
		// selected period: expected meters = configured grid meters of the scope
		int meters = gridMeters.size();
		int elapsedDays = 0;
		int daysWithData = 0;
		int completeDays = 0;
		int pricedDays = 0;
		int pricedCompleteDays = 0;
		Map<MeterData, Integer> missingHours = new LinkedHashMap<>();
		// meters with a whole day of the period without any row (NO_DATA)
		Set<MeterData> noDataDay = new HashSet<>();

		for (LocalDate day = current.from(); day.isBefore(current.to()); day = day.plusDays(1)) {
			String weekday = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			Acc acc = days.get(day);

			if (day.atStartOfDay().isBefore(dataUntil)) {
				elapsedDays++;
			}

			// hours of the day up to dataUntil (0 after dataUntil / before historyStartDate)
			int dayHours = expectedHours(day, dataFrom, dataUntil);
			long expectedMeterHours = (long) meters * dayHours;
			int reportingMeters = 0;
			long issuesStarted = System.nanoTime();

			for (MeterData meter : gridMeters) {
				int valid = meter.validHours(day);

				if (valid > 0) {
					reportingMeters++;
				}

				if (valid < dayHours) {
					missingHours.merge(meter, dayHours - valid, Integer::sum);

					// same rule as missingMeters(): no row on that day = NO_DATA
					if (!meter.hours.containsKey(day)) {
						noDataDay.add(meter);
					}
				}
			}

			List<MissingMeter> missingMeters = missingMeters(gridMeters, day, dayHours);
			timer.add("meterIssues", System.nanoTime() - issuesStarted);

			// an EVN day always counts (total and average), measured data or not
			Acc dayOfficial = official.get(day);
			boolean evnDay = dayOfficial != null && dayOfficial.fromEvn;

			if (evnDay) {
				officialTotal.add(dayOfficial, pricedOn.test(day));
				officialAverage.add(dayOfficial, pricedOn.test(day));
			}

			if (acc == null || !acc.hasData()) {
				// only outliers on that day: it has data, none of it usable
				boolean onlyOutliers = acc != null && acc.excluded.signum() > 0;

				daily.add(new DayPoint(day.toString(), weekday, 0, meters,
						onlyOutliers && expectedMeterHours > 0 ? BigDecimal.ZERO.setScale(1) : null,
						onlyOutliers, onlyOutliers ? excludedMwh(acc.excluded) : null, null, null,
						reportingMeters, missingMeters, null, evn(evn.get(day)),
						// EVN day without measured data: solar null, total = totalElectricity
						official(dayOfficial, pricedOn.test(day))));
				continue;
			}

			boolean dayComplete = isComplete(day, acc, dataUntil, expectedMeterHours);

			total.add(acc);
			daysWithData++;

			if (!evnDay) {
				officialTotal.add(acc, pricedOn.test(day));
			}

			if (pricedOn.test(day)) {
				pricedDays++;
			}

			if (dayComplete) {
				complete.add(acc);
				completeDays++;

				if (!evnDay) {
					officialAverage.add(acc, pricedOn.test(day));
				}

				if (pricedOn.test(day)) {
					pricedCompleteDays++;
				}
			}

			daily.add(new DayPoint(
					day.toString(),
					weekday,
					acc.hoursWithData(),
					meters,
					coveragePct(acc, expectedMeterHours),
					!dayComplete,
					excludedMwh(acc.excluded),
					mwh(acc, BigDecimal.ONE, pricedOn.test(day)),
					kusd(acc, BigDecimal.ONE, pricedOn.test(day)),
					reportingMeters,
					missingMeters,
					calibrated(acc, pricedOn.test(day)),
					evn(evn.get(day)),
					official(dayOfficial, pricedOn.test(day))
			));
		}

		long issuesStarted = System.nanoTime();
		List<MeterIssue> meterIssues = new ArrayList<>(missingHours.size());
		BigDecimal estimatedMissingKwh = null;
		// missing kWh is spread over every band -> priced at the scope's average
		// measured grid price of the period (null when no priced day)
		BigDecimal usdPerKwh = priced && pricedDays > 0 ? total.gridUsdPerKwh() : null;

		for (Map.Entry<MeterData, Integer> entry : missingHours.entrySet()) {
			MeterData meter = entry.getKey();
			int missing = entry.getValue();
			boolean major = noDataDay.contains(meter) || missing >= HOURS_PER_DAY;

			// MAJOR only, and only meters with valid hours to take the rate from
			BigDecimal meterMissingKwh = null;
			if (major && meter.validHoursTotal > 0) {
				BigDecimal perHour = meter.validKwh.divide(
						BigDecimal.valueOf(meter.validHoursTotal), 6, RoundingMode.HALF_UP);
				meterMissingKwh = perHour.multiply(BigDecimal.valueOf(missing));
				estimatedMissingKwh = estimatedMissingKwh == null
						? meterMissingKwh : estimatedMissingKwh.add(meterMissingKwh);
			}

			meterIssues.add(new MeterIssue(meter.meter, meter.fac, missing,
					formatTsOrNull(meter.lastDataAt), major ? MAJOR : MINOR,
					meterMissingKwh == null ? null : out(meterMissingKwh, THOUSAND),
					meterMissingKwh == null || usdPerKwh == null
							? null : out(meterMissingKwh.multiply(usdPerKwh), THOUSAND)));
		}
		meterIssues.sort(ISSUE_ORDER);
		timer.add("meterIssues", System.nanoTime() - issuesStarted);

		BigDecimal divisor = BigDecimal.valueOf(Math.max(completeDays, 1));
		// money only when at least one summed / averaged day has a tariff version
		boolean totalPriced = priced && pricedDays > 0;
		boolean averagePriced = priced && pricedCompleteDays > 0;

		return new Scope(
				def.name(),
				def.displayName,
				monthly,
				daily,
				meters,
				elapsedDays,
				daysWithData,
				completeDays,
				mwh(total, BigDecimal.ONE, totalPriced),
				kusd(total, BigDecimal.ONE, totalPriced),
				completeDays == 0 ? null : mwh(complete, divisor, averagePriced),
				completeDays == 0 ? null : kusd(complete, divisor, averagePriced),
				meterIssues,
				pricedDays < daysWithData,
				estimatedMissingKwh == null ? null : out(estimatedMissingKwh, THOUSAND),
				// EVN days always included (see OfficialSum)
				officialTotal.mwh(this, false, priced),
				officialTotal.kusd(this, false, priced),
				officialAverage.mwh(this, true, priced),
				officialAverage.kusd(this, true, priced),
				officialTotal.evnDays,
				officialTotal.measuredDays
		);
	}

	// meters with fewer valid hours than dayHours: NO_DATA first, then fewest hours, then name
	private List<MissingMeter> missingMeters(List<MeterData> gridMeters, LocalDate day, int dayHours) {
		if (dayHours <= 0) {
			return List.of();
		}

		List<MissingMeter> missing = new ArrayList<>();

		for (MeterData meter : gridMeters) {
			int[] h = meter.hours.get(day);
			int valid = h == null ? 0 : h[0];

			if (valid >= dayHours) {
				continue;
			}

			String reason = h == null ? NO_DATA : h[1] > 0 ? OUTLIER : PARTIAL;
			missing.add(new MissingMeter(meter.meter, meter.fac, valid, reason, formatTsOrNull(meter.lastDataAt)));
		}

		missing.sort(MISSING_ORDER);
		return missing;
	}

	// hours of day within [dataFrom, dataUntil)
	private static int expectedHours(LocalDate day, LocalDateTime dataFrom, LocalDateTime dataUntil) {
		LocalDateTime start = day.atStartOfDay();
		LocalDateTime end = day.plusDays(1).atStartOfDay();

		if (start.isBefore(dataFrom)) {
			start = dataFrom;
		}

		if (end.isAfter(dataUntil)) {
			end = dataUntil;
		}

		return start.isBefore(end) ? (int) ChronoUnit.HOURS.between(start, end) : 0;
	}

	// day is over and meter-hours >= minCoveragePct of expectedMeterHours
	private boolean isComplete(LocalDate day, Acc acc, LocalDateTime dataUntil, long expectedMeterHours) {
		return expectedMeterHours > 0
				&& !day.plusDays(1).atStartOfDay().isAfter(dataUntil)
				&& BigDecimal.valueOf(acc.meterHours()).multiply(BigDecimal.valueOf(100))
				.compareTo(properties.getMinCoveragePct()
						.multiply(BigDecimal.valueOf(expectedMeterHours))) >= 0;
	}

	// most grid meters reporting in one hour within +/- meterWindowDays of day
	private int expectedMeters(Map<LocalDate, Acc> days, LocalDate day) {
		int window = Math.max(properties.getMeterWindowDays(), 0);
		int meters = 0;

		for (LocalDate d = day.minusDays(window); !d.isAfter(day.plusDays(window)); d = d.plusDays(1)) {
			Acc acc = days.get(d);

			if (acc != null) {
				meters = Math.max(meters, acc.maxMeters());
			}
		}

		return meters;
	}

	// most grid meters reporting in one hour of the period
	private int meters(Map<LocalDate, Acc> days, Period period) {
		int meters = 0;

		for (Map.Entry<LocalDate, Acc> entry : days.entrySet()) {
			LocalDate day = entry.getKey();

			if (!day.isBefore(period.from()) && day.isBefore(period.to())) {
				meters = Math.max(meters, entry.getValue().maxMeters());
			}
		}

		return meters;
	}

	private BigDecimal coveragePct(Acc acc, long expectedMeterHours) {
		if (expectedMeterHours <= 0) {
			return null;
		}

		return BigDecimal.valueOf(acc.meterHours())
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(expectedMeterHours), 1, RoundingMode.HALF_UP);
	}

	// prices of the tariff version in effect on the day of dataUntil; null when none
	private UnitPrices unitPrices(Optional<Pricing> pricing, LocalDate day) {
		if (pricing.isEmpty() || !pricing.get().priced(day)) {
			return null;
		}

		Pricing p = pricing.get();

		return new UnitPrices(
				price(p, TouBand.NORMAL, p.gridFactor(), day),
				price(p, TouBand.HIGH_PEAK, p.gridFactor(), day),
				price(p, TouBand.LOW_PEAK, p.gridFactor(), day),
				price(p, TouBand.NORMAL, p.solarFactor().get(TouBand.NORMAL), day),
				price(p, TouBand.HIGH_PEAK, p.solarFactor().get(TouBand.HIGH_PEAK), day),
				price(p, TouBand.LOW_PEAK, p.solarFactor().get(TouBand.LOW_PEAK), day),
				p.effectiveFrom(day).map(LocalDate::toString).orElse(null)
		);
	}

	private BigDecimal price(Pricing pricing, TouBand band, BigDecimal factor, LocalDate day) {
		return pricing.weekdayVnd(band, day)
				.map(vnd -> vnd.multiply(factor).setScale(4, RoundingMode.HALF_UP))
				.orElse(null);
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	// kWh / divisor -> MWh; null when no data, bands (= grid per band) null
	// when not priced, solar null when no SOLAR meter data;
	// totalElectricity = grid (bought from EVN), load = total = sub-meter sum
	private Values mwh(Acc acc, BigDecimal divisor, boolean priced) {
		if (!acc.hasData()) {
			return null;
		}

		BigDecimal div = divisor.multiply(THOUSAND);
		BigDecimal load = out(acc.load, div);

		return new Values(
				priced ? out(acc.bandKwh.get(TouBand.NORMAL), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.HIGH_PEAK), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.LOW_PEAK), div) : null,
				acc.hasSolar ? out(acc.solar, div) : null,
				out(acc.grid, div),
				load,
				load
		);
	}

	// USD / divisor -> K$; null when no data or not priced;
	// totalElectricity = grid money, load = total = grid money + solar money
	private Values kusd(Acc acc, BigDecimal divisor, boolean priced) {
		if (!priced || !acc.hasData()) {
			return null;
		}

		BigDecimal div = divisor.multiply(THOUSAND);
		BigDecimal gridUsd = acc.gridUsd();
		BigDecimal load = out(gridUsd.add(acc.solarUsd), div);

		return new Values(
				out(acc.bandUsd.get(TouBand.NORMAL), div),
				out(acc.bandUsd.get(TouBand.HIGH_PEAK), div),
				out(acc.bandUsd.get(TouBand.LOW_PEAK), div),
				acc.hasSolar ? out(acc.solarUsd, div) : null,
				out(gridUsd, div),
				load,
				load
		);
	}

	// measured grid + loss (one day) -> MWh; null when no calibration covers
	// the day or no hour with grid data; bands null when not priced
	private Calibrated calibrated(Acc acc, boolean priced) {
		if (acc.calIds == null || acc.calHours == 0) {
			return null;
		}

		return new Calibrated(
				priced ? out(acc.calBandKwh.get(TouBand.NORMAL), THOUSAND) : null,
				priced ? out(acc.calBandKwh.get(TouBand.HIGH_PEAK), THOUSAND) : null,
				priced ? out(acc.calBandKwh.get(TouBand.LOW_PEAK), THOUSAND) : null,
				out(acc.calGrid, THOUSAND),
				acc.calLossPerHour,
				acc.calHours,
				acc.calIds,
				true
		);
	}

	// official day (officialDays) -> MWh / K$ with its source; MEASURED = the
	// measured Acc itself, so mwh / kusd are copied exactly; null = no data
	private Official official(Acc acc, boolean priced) {
		if (acc == null || !acc.hasData()) {
			return null;
		}

		return new Official(mwh(acc, BigDecimal.ONE, priced), kusd(acc, BigDecimal.ONE, priced),
				acc.fromEvn ? "EVN" : "MEASURED");
	}

	// F2_Utility_Evn_Daily row (kWh) -> MWh, null = no row
	private Evn evn(EvnDay row) {
		if (row == null) {
			return null;
		}

		return new Evn(
				out(row.normalKwh(), THOUSAND),
				out(row.highKwh(), THOUSAND),
				out(row.lowKwh(), THOUSAND),
				out(row.totalKwh(), THOUSAND),
				row.solarKwh() == null ? null : out(row.solarKwh(), THOUSAND),
				row.sourceFile(),
				formatTsOrNull(row.importedAt())
		);
	}

	// kWh dropped as outliers -> MWh, null when nothing was dropped
	private BigDecimal excludedMwh(BigDecimal kwh) {
		return kwh.signum() == 0 ? null : out(kwh, THOUSAND);
	}

	private BigDecimal out(BigDecimal value, BigDecimal divisor) {
		return value.divide(divisor, OUT_SCALE, RoundingMode.HALF_UP);
	}

	// "Sep (Aug21 ~ Sep20]" like cell C5 of the sheet: bill month = month the period ends in
	private String label(Period period) {
		LocalDate last = period.to().minusDays(1);

		return String.format(Locale.ENGLISH, "%s (%s%d ~ %s%d]",
				shortMonth(period.month().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)),
				shortMonth(period.from().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)),
				period.from().getDayOfMonth(),
				shortMonth(last.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)),
				last.getDayOfMonth());
	}

	private String shortMonth(String value) {
		return value.length() > 3 ? value.substring(0, 3) : value;
	}

	private String formatTsOrNull(LocalDateTime value) {
		return value == null ? null : formatTs(value);
	}

	private String formatTs(LocalDateTime value) {
		return value.atZone(APP_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}
