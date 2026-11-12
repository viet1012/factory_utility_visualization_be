package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.config.UtilityFinanceProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.BillPeriod;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MonthPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.UnitPrices;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Values;
import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageHourProjection;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouTariff;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds GET /api/utility/electric-consumption from the hourly History_Main
 * energy (PowerUsageRepo.findHourlyEnergy: fac x SOLAR flag x hour) and the
 * time-of-use tariff of F2_Utility_Cost_Master.
 *
 * The meters in the DB only give hourly kWh, so the Normal / High-peak /
 * Low-peak split of the sheet is rebuilt by putting each hour (or part of an
 * hour) into the band of its tariff time window. Grid = devices without any
 * SOLAR channel, solar = SOLAR devices (same split as /power-usage).
 */
@Service
@RequiredArgsConstructor
public class ElectricConsumptionCalculator {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String ENERGY_NAME = "Total Energy Consumption";

	// no purpose cabinet needed; purpose_key always contains '|', so '' never matches
	private static final List<String> NO_PURPOSE_KEYS = List.of("");

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	private static final int OUT_SCALE = 3;

	private final PowerUsageRepo repo;
	private final TouTariffService tariffService;
	private final ElectricConsumptionProperties properties;

	// the 3 charts of the sheet: TTL KVH, Fac A&B (Trans No.2), Fac C (Trans No.1)
	enum ScopeDef {
		KVH("TTL KVH: A&B + C", Set.of()),
		FAC_AB("Fac A&B", Set.of("FAC_A", "FAC_B")),
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
		}

		private BigDecimal gridUsd() {
			return bandUsd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		}
	}

	// USD per kWh per VND of tariff
	private record Pricing(TouTariff tariff, BigDecimal gridFactor, BigDecimal solarFactor) {
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

	@Transactional(readOnly = true)
	public ElectricConsumptionResponseDto calculate(YearMonth month, int months) {
		LocalDateTime lastFullHour = LocalDateTime.now(APP_ZONE).truncatedTo(ChronoUnit.HOURS);

		Period current = period(month);
		List<Period> history = new ArrayList<>(months);

		for (int i = months; i >= 1; i--) {
			history.add(period(month.minusMonths(i)));
		}

		LocalDateTime queryFrom = (history.isEmpty() ? current : history.get(0)).from().atStartOfDay();
		LocalDateTime currentEnd = current.to().atStartOfDay();
		LocalDateTime dataUntil = currentEnd.isAfter(lastFullHour) ? lastFullHour : currentEnd;

		Optional<Pricing> pricing = pricing();
		Map<ScopeDef, Map<LocalDate, Acc>> days = loadDays(queryFrom, dataUntil, pricing);

		List<Scope> scopes = new ArrayList<>(ScopeDef.values().length);

		for (ScopeDef def : ScopeDef.values()) {
			scopes.add(scope(def, days.get(def), history, current, dataUntil, pricing.isPresent()));
		}

		return new ElectricConsumptionResponseDto(
				month.toString(),
				new BillPeriod(label(current), current.from().toString(), current.to().toString(), current.days()),
				dataUntil.isAfter(current.from().atStartOfDay()) ? formatTs(dataUntil) : null,
				pricing.isPresent(),
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
			Optional<Pricing> pricing
	) {
		Map<ScopeDef, Map<LocalDate, Acc>> days = new EnumMap<>(ScopeDef.class);

		for (ScopeDef def : ScopeDef.values()) {
			days.put(def, new HashMap<>());
		}

		if (!from.isBefore(to)) {
			return days;
		}

		// rows are stamped at the end of their hour
		List<PowerUsageHourProjection> rows = repo.findHourlyEnergy(
				ENERGY_NAME, NO_PURPOSE_KEYS, from.plusHours(1), to.plusHours(1));

		for (PowerUsageHourProjection row : rows) {
			LocalDateTime hourAt = row.getHourAt();

			if (hourAt == null || row.getKwh() == null || hourAt.isBefore(from) || !hourAt.isBefore(to)) {
				continue;
			}

			Acc hour = hourAcc(row, pricing);
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
	private Acc hourAcc(PowerUsageHourProjection row, Optional<Pricing> pricing) {
		Acc acc = new Acc();
		BigDecimal kwh = row.getKwh();
		boolean solar = row.getSolar() != null && row.getSolar() == 1;

		if (solar) {
			acc.solar = kwh;
		} else {
			acc.grid = kwh;
		}

		if (pricing.isEmpty()) {
			return acc;
		}

		for (TouShare share : pricing.get().tariff().shares(row.getHourAt())) {
			BigDecimal part = kwh.multiply(share.fraction());
			BigDecimal vnd = part.multiply(share.vnd());

			if (solar) {
				acc.solarUsd = acc.solarUsd.add(vnd.multiply(pricing.get().solarFactor()));
			} else {
				acc.bandKwh.merge(share.band(), part, BigDecimal::add);
				acc.bandUsd.merge(share.band(), vnd.multiply(pricing.get().gridFactor()), BigDecimal::add);
			}
		}

		return acc;
	}

	private Optional<Pricing> pricing() {
		Optional<BigDecimal> gridFactor = factor(properties.getGrid(), BigDecimal.ONE);
		Optional<BigDecimal> solarFactor = factor(properties.getSolar(), properties.getSolarPriceFactor());

		if (gridFactor.isEmpty() || solarFactor.isEmpty()) {
			return Optional.empty();
		}

		return tariffService.load().map(tariff -> new Pricing(tariff, gridFactor.get(), solarFactor.get()));
	}

	// multiplier * sepzone / exchangeRate
	private Optional<BigDecimal> factor(UtilityFinanceProperties.Policy policy, BigDecimal multiplier) {
		if (policy == null || policy.getExchangeRate() == null || policy.getExchangeRate().signum() == 0
				|| policy.getSepzone() == null || multiplier == null) {
			return Optional.empty();
		}

		return Optional.of(multiplier.multiply(policy.getSepzone())
				.divide(policy.getExchangeRate(), 12, RoundingMode.HALF_UP));
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
			Acc sum = sumDays(days, period.from(), period.to());
			BigDecimal divisor = BigDecimal.valueOf(period.days());

			monthly.add(new MonthPoint(
					period.month().toString(),
					label(period),
					period.from().toString(),
					period.to().toString(),
					period.days(),
					mwh(sum, divisor, priced),
					kusd(sum, divisor, priced)
			));
		}

		List<DayPoint> daily = new ArrayList<>(current.days());
		Acc total = new Acc();
		int elapsedDays = 0;

		for (LocalDate day = current.from(); day.isBefore(current.to()); day = day.plusDays(1)) {
			String weekday = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

			if (!day.atStartOfDay().isBefore(dataUntil)) {
				daily.add(new DayPoint(day.toString(), weekday, null, null));
				continue;
			}

			Acc acc = days.getOrDefault(day, new Acc());
			total.add(acc);
			elapsedDays++;

			daily.add(new DayPoint(
					day.toString(),
					weekday,
					mwh(acc, BigDecimal.ONE, priced),
					kusd(acc, BigDecimal.ONE, priced)
			));
		}

		BigDecimal elapsed = BigDecimal.valueOf(Math.max(elapsedDays, 1));

		return new Scope(
				def.name(),
				def.displayName,
				monthly,
				daily,
				mwh(total, BigDecimal.ONE, priced),
				kusd(total, BigDecimal.ONE, priced),
				elapsedDays == 0 ? null : mwh(total, elapsed, priced),
				elapsedDays == 0 ? null : kusd(total, elapsed, priced),
				elapsedDays
		);
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
				price(p, TouBand.NORMAL, p.solarFactor()),
				price(p, TouBand.HIGH_PEAK, p.solarFactor()),
				price(p, TouBand.LOW_PEAK, p.solarFactor())
		);
	}

	private BigDecimal price(Pricing pricing, TouBand band, BigDecimal factor) {
		return pricing.tariff().weekdayRate(band)
				.map(vnd -> vnd.multiply(factor).setScale(4, RoundingMode.HALF_UP))
				.orElse(null);
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private Acc sumDays(Map<LocalDate, Acc> days, LocalDate from, LocalDate to) {
		Acc sum = new Acc();

		days.forEach((day, acc) -> {
			if (!day.isBefore(from) && day.isBefore(to)) {
				sum.add(acc);
			}
		});

		return sum;
	}

	// kWh / divisor -> MWh; bands null when not priced
	private Values mwh(Acc acc, BigDecimal divisor, boolean priced) {
		BigDecimal div = divisor.multiply(THOUSAND);

		return new Values(
				priced ? out(acc.bandKwh.get(TouBand.NORMAL), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.HIGH_PEAK), div) : null,
				priced ? out(acc.bandKwh.get(TouBand.LOW_PEAK), div) : null,
				out(acc.solar, div),
				out(acc.grid, div),
				out(acc.grid.add(acc.solar), div)
		);
	}

	// USD / divisor -> K$; null when not priced
	private Values kusd(Acc acc, BigDecimal divisor, boolean priced) {
		if (!priced) {
			return null;
		}

		BigDecimal div = divisor.multiply(THOUSAND);
		BigDecimal gridUsd = acc.gridUsd();

		return new Values(
				out(acc.bandUsd.get(TouBand.NORMAL), div),
				out(acc.bandUsd.get(TouBand.HIGH_PEAK), div),
				out(acc.bandUsd.get(TouBand.LOW_PEAK), div),
				out(acc.solarUsd, div),
				out(gridUsd, div),
				out(gridUsd.add(acc.solarUsd), div)
		);
	}

	private BigDecimal out(BigDecimal value, BigDecimal divisor) {
		return value.divide(divisor, OUT_SCALE, RoundingMode.HALF_UP);
	}

	// "Sep (Aug21 ~ Sep20]" like cell C5 of the sheet
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
