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
import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionHourProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionMeterDayProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionMeterProjection;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import lombok.RequiredArgsConstructor;
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
import java.util.function.Predicate;

/**
 * Builds GET /api/utility/electric-consumption from the hourly History_Main
 * energy (ElectricConsumptionRepo.findHourlyEnergy: fac x SOLAR flag x hour x
 * outlier flag) and the time-of-use tariff of F2_Utility_Cost_Master.
 *
 * Outlier meter-hours (cumulative / catch-up values, see the repo) are not
 * counted: that meter-hour is treated as missing, so the day becomes
 * partial, and the dropped kWh is reported as excludedMwh.
 *
 * The meters in the DB only give hourly kWh, so the Normal / High-peak /
 * Low-peak split of the sheet is rebuilt by putting each hour (or part of an
 * hour) into the band of its tariff time window. Grid = devices without any
 * SOLAR channel, solar = SOLAR devices (same split as /power-usage); the
 * split uses the CURRENT channel config, so a solar meter that had no SOLAR
 * channel in the past is counted as grid for that time.
 *
 * Coverage of the selected period is measured against the CONFIGURED grid
 * meters of the scope (ElectricConsumptionRepo.findGridMeters), so a meter
 * that stopped reporting stays in the denominator; per day / per period the
 * meters with missing hours are listed (findMeterDays). The monthly history
 * still uses the meters seen in the data (+/- meterWindowDays).
 */
@Service
@RequiredArgsConstructor
public class ElectricConsumptionCalculator {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String ENERGY_NAME = "Total Energy Consumption";

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	private static final int OUT_SCALE = 3;

	private static final int HOURS_PER_DAY = 24;

	private final ElectricConsumptionRepo repo;
	private final ElectricityPricingService pricingService;
	private final ElectricConsumptionProperties properties;

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
	record Period(YearMonth month, LocalDate from, LocalDate to) {
		int days() {
			return (int) ChronoUnit.DAYS.between(from, to);
		}
	}

	// kWh / USD of one scope on one day (or summed over several days)
	private static final class Acc {
		private final Map<TouBand, BigDecimal> bandKwh = new EnumMap<>(TouBand.class);
		private final Map<TouBand, BigDecimal> bandUsd = new EnumMap<>(TouBand.class);
		private BigDecimal grid = BigDecimal.ZERO;
		private BigDecimal solar = BigDecimal.ZERO;
		private BigDecimal solarUsd = BigDecimal.ZERO;
		private boolean hasGrid;
		private boolean hasSolar;
		// grid meters reporting in hour h of the day (meaningful for one day only)
		private final int[] gridMeters = new int[HOURS_PER_DAY];
		// kWh dropped as outliers
		private BigDecimal excluded = BigDecimal.ZERO;

		private Acc() {
			for (TouBand band : TouBand.values()) {
				bandKwh.put(band, BigDecimal.ZERO);
				bandUsd.put(band, BigDecimal.ZERO);
			}
		}

		private void add(Acc other) {
			other.bandKwh.forEach((band, value) -> bandKwh.merge(band, value, BigDecimal::add));
			other.bandUsd.forEach((band, value) -> bandUsd.merge(band, value, BigDecimal::add));
			grid = grid.add(other.grid);
			solar = solar.add(other.solar);
			solarUsd = solarUsd.add(other.solarUsd);
			hasGrid |= other.hasGrid;
			hasSolar |= other.hasSolar;
			excluded = excluded.add(other.excluded);

			for (int h = 0; h < HOURS_PER_DAY; h++) {
				gridMeters[h] += other.gridMeters[h];
			}
		}

		private boolean hasData() {
			return hasGrid || hasSolar;
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

	// upper-case box_device_id lists for the repo (never empty)
	private record MeterFilter(List<String> excluded, List<String> facAb, List<String> facC) {}

	private static final String NO_DATA = "NO_DATA";
	private static final String PARTIAL = "PARTIAL";
	private static final String OUTLIER = "OUTLIER";

	// MeterIssue.severity
	private static final String MAJOR = "MAJOR";
	private static final String MINOR = "MINOR";

	private static final Comparator<MissingMeter> MISSING_ORDER = Comparator
			.comparing((MissingMeter m) -> !NO_DATA.equals(m.reason()))
			.thenComparingInt(MissingMeter::hours)
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

		MeterFilter filter = meterFilter();
		Optional<Pricing> pricing = pricingService.load();
		Map<ScopeDef, Map<LocalDate, Acc>> days = loadDays(queryFrom, dataUntil, hourOffset, pricing, filter);
		Map<ScopeDef, List<MeterData>> meters = loadMeters(queryFrom, dataFrom, dataUntil, hourOffset, filter);

		List<Scope> scopes = new ArrayList<>(ScopeDef.values().length);

		for (ScopeDef def : ScopeDef.values()) {
			scopes.add(scope(def, days.get(def), meters.get(def), history, current, dataFrom, dataUntil,
					pricing.isPresent(),
					day -> pricing.isPresent() && pricing.get().priced(day)));
		}

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
		ElectricConsumptionProperties.MeterScope meterScope = properties.getMeterScope();

		return new MeterFilter(
				pricingService.excludedMeterKeys(),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacAb()),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacC()));
	}

	/**
	 * Configured grid meters per scope, with their valid / outlier hours per
	 * day of [dataFrom, dataUntil). The outlier window is [from, dataUntil),
	 * the same as loadDays, so both see the same outliers.
	 */
	private Map<ScopeDef, List<MeterData>> loadMeters(
			LocalDateTime from,
			LocalDateTime dataFrom,
			LocalDateTime dataUntil,
			int offset,
			MeterFilter filter
	) {
		Map<String, MeterData> byMeter = new LinkedHashMap<>();

		for (ElectricConsumptionMeterProjection row : repo.findGridMeters(
				ENERGY_NAME, filter.excluded(), filter.facAb(), filter.facC())) {
			if (row.getMeter() != null) {
				byMeter.putIfAbsent(row.getMeter().trim(), new MeterData(row.getMeter().trim(), facKey(row.getFac())));
			}
		}

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

		Map<ScopeDef, List<MeterData>> meters = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			meters.put(def, byMeter.values().stream().filter(m -> def.includes(m.fac)).toList());
		}

		return meters;
	}

	private static String facKey(String fac) {
		return fac == null ? "" : fac.trim().toUpperCase(Locale.ROOT);
	}

	private Map<ScopeDef, Map<LocalDate, Acc>> loadDays(
			LocalDateTime from,
			LocalDateTime to,
			int offset,
			Optional<Pricing> pricing,
			MeterFilter filter
	) {
		Map<ScopeDef, Map<LocalDate, Acc>> days = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			days.put(def, new HashMap<>());
		}

		if (!from.isBefore(to)) {
			return days;
		}

		// rows are stamped at the end of their hour; hourOffset shifts every
		// hour, so the query window is shifted the other way
		List<ElectricConsumptionHourProjection> rows = repo.findHourlyEnergy(
				ENERGY_NAME, from.plusHours(1L - offset), to.plusHours(1L - offset),
				properties.getOutlierRatio(), properties.getSolarOutlierRatio(), properties.getOutlierMinKwh(),
				filter.excluded(), filter.facAb(), filter.facC());

		for (ElectricConsumptionHourProjection row : rows) {
			if (row.getHourAt() == null || row.getKwh() == null) {
				continue;
			}

			LocalDateTime hourAt = row.getHourAt().plusHours(offset);

			if (hourAt.isBefore(from) || !hourAt.isBefore(to)) {
				continue;
			}

			Acc hour = hourAcc(row, hourAt, pricing);
			String fac = facKey(row.getFac());
			LocalDate day = hourAt.toLocalDate();

			for (ScopeDef def : ScopeDef.values()) {
				if (def.includes(fac)) {
					days.get(def).computeIfAbsent(day, key -> new Acc()).add(hour);
				}
			}
		}

		return days;
	}

	// one History_Main row split into tariff bands
	private Acc hourAcc(ElectricConsumptionHourProjection row, LocalDateTime hourAt, Optional<Pricing> pricing) {
		Acc acc = new Acc();
		BigDecimal kwh = row.getKwh();
		boolean solar = row.getSolar() != null && row.getSolar() == 1;

		// dropped: counts neither as energy nor as a reporting meter-hour
		if (row.getOutlier() != null && row.getOutlier() == 1) {
			acc.excluded = kwh;
			return acc;
		}

		if (solar) {
			acc.solar = kwh;
			acc.hasSolar = true;
		} else {
			acc.grid = kwh;
			acc.hasGrid = true;
			acc.gridMeters[hourAt.getHour()] = row.getDevices() == null ? 1 : row.getDevices();
		}

		if (pricing.isEmpty()) {
			return acc;
		}

		Pricing p = pricing.get();

		for (TouShare share : p.shares(hourAt)) {
			if (solar) {
				acc.solarUsd = acc.solarUsd.add(p.solarUsd(kwh, share));
			} else {
				acc.bandKwh.merge(share.band(), kwh.multiply(share.fraction()), BigDecimal::add);
				acc.bandUsd.merge(share.band(), p.gridUsd(kwh, share), BigDecimal::add);
			}
		}

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
			Predicate<LocalDate> pricedOn
	) {
		List<MonthPoint> monthly = new ArrayList<>(history.size());

		for (Period period : history) {
			Acc complete = new Acc();
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

				if (acc == null || !acc.hasData()) {
					continue;
				}

				daysWithData++;

				// history: expected meters still from the data (unchanged)
				if (isComplete(day, acc, dataUntil, (long) expectedMeters(days, day) * HOURS_PER_DAY)) {
					complete.add(acc);
					completeDays++;

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
					pricedCompleteDays < completeDays
			));
		}

		List<DayPoint> daily = new ArrayList<>(current.days());
		Acc total = new Acc();
		Acc complete = new Acc();
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

			if (acc == null || !acc.hasData()) {
				// only outliers on that day: it has data, none of it usable
				boolean onlyOutliers = acc != null && acc.excluded.signum() > 0;

				daily.add(new DayPoint(day.toString(), weekday, 0, meters,
						onlyOutliers && expectedMeterHours > 0 ? BigDecimal.ZERO.setScale(1) : null,
						onlyOutliers, onlyOutliers ? excludedMwh(acc.excluded) : null, null, null,
						reportingMeters, missingMeters));
				continue;
			}

			boolean dayComplete = isComplete(day, acc, dataUntil, expectedMeterHours);

			total.add(acc);
			daysWithData++;

			if (pricedOn.test(day)) {
				pricedDays++;
			}

			if (dayComplete) {
				complete.add(acc);
				completeDays++;

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
					missingMeters
			));
		}

		List<MeterIssue> meterIssues = new ArrayList<>(missingHours.size());
		BigDecimal estimatedMissingKwh = null;

		for (Map.Entry<MeterData, Integer> entry : missingHours.entrySet()) {
			MeterData meter = entry.getKey();
			int missing = entry.getValue();
			boolean major = noDataDay.contains(meter) || missing >= HOURS_PER_DAY;

			meterIssues.add(new MeterIssue(meter.meter, meter.fac, missing,
					formatTsOrNull(meter.lastDataAt), major ? MAJOR : MINOR));

			// MAJOR only, and only meters with valid hours to take the rate from
			if (major && meter.validHoursTotal > 0) {
				BigDecimal perHour = meter.validKwh.divide(
						BigDecimal.valueOf(meter.validHoursTotal), 6, RoundingMode.HALF_UP);
				BigDecimal kwh = perHour.multiply(BigDecimal.valueOf(missing));
				estimatedMissingKwh = estimatedMissingKwh == null ? kwh : estimatedMissingKwh.add(kwh);
			}
		}
		meterIssues.sort(ISSUE_ORDER);

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
				estimatedMissingKwh == null ? null : out(estimatedMissingKwh, THOUSAND)
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

	// kWh / divisor -> MWh; null when no data, bands null when not priced,
	// solar null when no SOLAR meter data
	private Values mwh(Acc acc, BigDecimal divisor, boolean priced) {
		if (!acc.hasData()) {
			return null;
		}

		BigDecimal div = divisor.multiply(THOUSAND);

		return new Values(
				priced ? out(acc.bandKwh.get(TouBand.NORMAL), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.HIGH_PEAK), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.LOW_PEAK), div) : null,
				acc.hasSolar ? out(acc.solar, div) : null,
				out(acc.grid, div),
				out(acc.grid.add(acc.solar), div)
		);
	}

	// USD / divisor -> K$; null when no data or not priced
	private Values kusd(Acc acc, BigDecimal divisor, boolean priced) {
		if (!priced || !acc.hasData()) {
			return null;
		}

		BigDecimal div = divisor.multiply(THOUSAND);
		BigDecimal gridUsd = acc.gridUsd();

		return new Values(
				out(acc.bandUsd.get(TouBand.NORMAL), div),
				out(acc.bandUsd.get(TouBand.HIGH_PEAK), div),
				out(acc.bandUsd.get(TouBand.LOW_PEAK), div),
				acc.hasSolar ? out(acc.solarUsd, div) : null,
				out(gridUsd, div),
				out(gridUsd.add(acc.solarUsd), div)
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
