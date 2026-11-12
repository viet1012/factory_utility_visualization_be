package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.BillPeriod;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MonthPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.UnitPrices;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Values;
import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionHourProjection;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

	public Period period(YearMonth month) {
		int cutoff = properties.getBillingCutoffDay();
		return new Period(month, month.minusMonths(1).atDay(cutoff), month.atDay(cutoff));
	}

	// bill month that contains the given day
	public YearMonth billMonthOf(LocalDate day) {
		YearMonth month = YearMonth.from(day);
		return day.getDayOfMonth() >= properties.getBillingCutoffDay() ? month.plusMonths(1) : month;
	}

	// hour offset from utility.electric-consumption.hour-offset
	public ElectricConsumptionResponseDto calculate(YearMonth month, int months) {
		return calculate(month, months, properties.getHourOffset());
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

		Optional<Pricing> pricing = pricingService.load();
		Map<ScopeDef, Map<LocalDate, Acc>> days = loadDays(queryFrom, dataUntil, hourOffset, pricing);

		List<Scope> scopes = new ArrayList<>(ScopeDef.values().length);

		for (ScopeDef def : ScopeDef.values()) {
			scopes.add(scope(def, days.get(def), history, current, dataUntil, pricing.isPresent()));
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
				unitPrices(pricing),
				scopes
		);
	}

	// ---------------------------------------------------------------------
	// Data loading
	// ---------------------------------------------------------------------

	private Map<ScopeDef, Map<LocalDate, Acc>> loadDays(
			LocalDateTime from,
			LocalDateTime to,
			int offset,
			Optional<Pricing> pricing
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
		ElectricConsumptionProperties.MeterScope meterScope = properties.getMeterScope();
		List<ElectricConsumptionHourProjection> rows = repo.findHourlyEnergy(
				ENERGY_NAME, from.plusHours(1L - offset), to.plusHours(1L - offset),
				properties.getOutlierRatio(), properties.getSolarOutlierRatio(), properties.getOutlierMinKwh(),
				pricingService.excludedMeterKeys(),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacAb()),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacC()));

		for (ElectricConsumptionHourProjection row : rows) {
			if (row.getHourAt() == null || row.getKwh() == null) {
				continue;
			}

			LocalDateTime hourAt = row.getHourAt().plusHours(offset);

			if (hourAt.isBefore(from) || !hourAt.isBefore(to)) {
				continue;
			}

			Acc hour = hourAcc(row, hourAt, pricing);
			String fac = row.getFac() == null ? "" : row.getFac().trim().toUpperCase(Locale.ROOT);
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
			List<Period> history,
			Period current,
			LocalDateTime dataUntil,
			boolean priced
	) {
		List<MonthPoint> monthly = new ArrayList<>(history.size());

		for (Period period : history) {
			Acc complete = new Acc();
			int meters = meters(days, period);
			int daysWithData = 0;
			int completeDays = 0;
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

				if (isComplete(day, acc, dataUntil, expectedMeters(days, day))) {
					complete.add(acc);
					completeDays++;
				}
			}

			BigDecimal divisor = BigDecimal.valueOf(Math.max(completeDays, 1));

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
					completeDays == 0 ? null : mwh(complete, divisor, priced),
					completeDays == 0 ? null : kusd(complete, divisor, priced)
			));
		}

		List<DayPoint> daily = new ArrayList<>(current.days());
		Acc total = new Acc();
		Acc complete = new Acc();
		int meters = meters(days, current);
		int elapsedDays = 0;
		int daysWithData = 0;
		int completeDays = 0;

		for (LocalDate day = current.from(); day.isBefore(current.to()); day = day.plusDays(1)) {
			String weekday = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			Acc acc = days.get(day);

			if (day.atStartOfDay().isBefore(dataUntil)) {
				elapsedDays++;
			}

			if (acc == null || !acc.hasData()) {
				// only outliers on that day: it has data, none of it usable
				boolean onlyOutliers = acc != null && acc.excluded.signum() > 0;

				int dayMeters = expectedMeters(days, day);

				daily.add(new DayPoint(day.toString(), weekday, 0, dayMeters,
						onlyOutliers && dayMeters > 0 ? BigDecimal.ZERO.setScale(1) : null,
						onlyOutliers, onlyOutliers ? excludedMwh(acc.excluded) : null, null, null));
				continue;
			}

			int dayMeters = expectedMeters(days, day);
			boolean dayComplete = isComplete(day, acc, dataUntil, dayMeters);

			total.add(acc);
			daysWithData++;

			if (dayComplete) {
				complete.add(acc);
				completeDays++;
			}

			daily.add(new DayPoint(
					day.toString(),
					weekday,
					acc.hoursWithData(),
					dayMeters,
					coveragePct(acc, dayMeters),
					!dayComplete,
					excludedMwh(acc.excluded),
					mwh(acc, BigDecimal.ONE, priced),
					kusd(acc, BigDecimal.ONE, priced)
			));
		}

		BigDecimal divisor = BigDecimal.valueOf(Math.max(completeDays, 1));

		return new Scope(
				def.name(),
				def.displayName,
				monthly,
				daily,
				meters,
				elapsedDays,
				daysWithData,
				completeDays,
				mwh(total, BigDecimal.ONE, priced),
				kusd(total, BigDecimal.ONE, priced),
				completeDays == 0 ? null : mwh(complete, divisor, priced),
				completeDays == 0 ? null : kusd(complete, divisor, priced)
		);
	}

	// day is over and meter-hour coverage >= minCoveragePct
	private boolean isComplete(LocalDate day, Acc acc, LocalDateTime dataUntil, int meters) {
		return meters > 0
				&& !day.plusDays(1).atStartOfDay().isAfter(dataUntil)
				&& BigDecimal.valueOf(acc.meterHours()).multiply(BigDecimal.valueOf(100))
				.compareTo(properties.getMinCoveragePct()
						.multiply(BigDecimal.valueOf((long) meters * HOURS_PER_DAY))) >= 0;
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

	private BigDecimal coveragePct(Acc acc, int meters) {
		if (meters == 0) {
			return null;
		}

		return BigDecimal.valueOf(acc.meterHours())
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf((long) meters * HOURS_PER_DAY), 1, RoundingMode.HALF_UP);
	}

	private UnitPrices unitPrices(Optional<Pricing> pricing) {
		if (pricing.isEmpty()) {
			return null;
		}

		Pricing p = pricing.get();

		return new UnitPrices(
				price(p, TouBand.NORMAL, p.gridFactor()),
				price(p, TouBand.HIGH_PEAK, p.gridFactor()),
				price(p, TouBand.LOW_PEAK, p.gridFactor()),
				price(p, TouBand.NORMAL, p.solarFactor().get(TouBand.NORMAL)),
				price(p, TouBand.HIGH_PEAK, p.solarFactor().get(TouBand.HIGH_PEAK)),
				price(p, TouBand.LOW_PEAK, p.solarFactor().get(TouBand.LOW_PEAK))
		);
	}

	private BigDecimal price(Pricing pricing, TouBand band, BigDecimal factor) {
		return pricing.weekdayVnd(band)
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

	private String formatTs(LocalDateTime value) {
		return value.atZone(APP_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}
