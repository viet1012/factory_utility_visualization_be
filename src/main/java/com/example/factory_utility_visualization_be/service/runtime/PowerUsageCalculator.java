package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.UtilityFinanceProperties;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.DayTotal;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.Factory;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.Forecast;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.Purpose;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.Supply;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto.Total;
import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageHourProjection;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouTariff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Builds GET /api/utility/power-usage from one hourly History_Main query
 * (fac x SOLAR flag x hour) plus F2_Utility_Cost_Master.
 *
 * Same meter split as the KVH daily data: grid = devices without any SOLAR
 * channel, solar = SOLAR devices, MTD rows with value > 0. Every kWh figure
 * (total, byFactory, last7Total, forecast) is grid + solar. Supply mix: grid
 * kWh by tariff band (no solar deduction) and SOLAR priced at tariff x 0.83
 * (same as /api/solar and /hourly). Bands come from the 3 distinct VND rates of
 * F2_Utility_Cost_Master (highest = HIGH_PEAK, lowest = LOW_PEAK), since the
 * table has no band column; anything else leaves supplyMix empty.
 *
 * Current period runs up to the last complete hour; the previous period
 * gets the same length (capped at the current period start).
 */
@Service
@RequiredArgsConstructor
public class PowerUsageCalculator {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String ENERGY_NAME = "Total Energy Consumption";

	private static final BigDecimal SOLAR_PRICE_FACTOR = new BigDecimal("0.83");

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private static final int LAST_DAYS = 7;

	// compressed air electricity cabinets: fac + box_id
	private static final List<PurposeCabinet> AIR_CABINETS = List.of(
			new PurposeCabinet("Fac_B", "ACP"),
			new PurposeCabinet("Fac_B", "ACP1"),
			new PurposeCabinet("Fac_B", "DB-COMP"),
			new PurposeCabinet("Fac_C", "ACP-1")
	);

	private static final List<String> AIR_CABINET_KEYS = AIR_CABINETS.stream()
			.map(PurposeCabinet::key)
			.toList();

	private final PowerUsageRepo repo;
	private final TouTariffService tariffService;
	private final UtilityFinanceProperties financeProperties;

	private enum Band {
		HIGH_PEAK,
		NORMAL,
		LOW_PEAK,
		SOLAR
	}

	// [from, to)
	private record Window(LocalDateTime from, LocalDateTime to) {
		boolean contains(LocalDateTime hour) {
			return !hour.isBefore(from) && hour.isBefore(to);
		}

		long hours() {
			return ChronoUnit.HOURS.between(from, to);
		}
	}

	// consumption of one hour, all facs
	private static final class HourUsage {
		// grid + solar per fac
		private final Map<String, BigDecimal> totalByFac = new HashMap<>();
		// grid kWh per purpose cabinet key
		private final Map<String, BigDecimal> gridByPurpose = new HashMap<>();
		private BigDecimal grid = BigDecimal.ZERO;
		private BigDecimal solar = BigDecimal.ZERO;

		private BigDecimal total() {
			return grid.add(solar);
		}
	}

	private record PurposeCabinet(String fac, String boxId) {
		// same shape as purpose_key in PowerUsageRepo.findHourlyEnergy
		String key() {
			return fac.toUpperCase(Locale.ROOT) + "|" + boxId.toUpperCase(Locale.ROOT);
		}

		String code() {
			return "COMPRESSED_AIR_" + fac.toUpperCase(Locale.ROOT) + "_" + boxId.toUpperCase(Locale.ROOT);
		}
	}

	@Transactional(readOnly = true)
	public PowerUsageResponseDto calculate(PowerUsagePeriod period, LocalDate date) {
		LocalDateTime now = LocalDateTime.now(APP_ZONE);
		LocalDateTime lastFullHour = now.truncatedTo(ChronoUnit.HOURS);
		LocalDate today = now.toLocalDate();

		LocalDateTime start = period.start(date);
		LocalDateTime end = period.end(start);
		boolean running = end.isAfter(lastFullHour);

		Window current = new Window(start, running ? max(start, lastFullHour) : end);
		Duration length = Duration.between(current.from(), current.to());
		LocalDateTime prevStart = period.previousStart(start);
		Window previous = new Window(prevStart, min(prevStart.plus(length), start));

		// last day shown in last7Total
		LocalDate lastDay = period == PowerUsagePeriod.DAY
				? date
				: running ? today : end.toLocalDate().minusDays(1);
		LocalDate last7First = lastDay.minusDays(LAST_DAYS - 1L);

		boolean withForecast = period != PowerUsagePeriod.WEEK;
		LocalDateTime monthStart = date.withDayOfMonth(1).atStartOfDay();
		LocalDateTime prevMonthStart = monthStart.minusMonths(1);

		LocalDateTime queryFrom = min(previous.from(), last7First.atStartOfDay());

		if (withForecast) {
			queryFrom = min(queryFrom, prevMonthStart);
		}

		Map<LocalDateTime, HourUsage> hours = loadHours(queryFrom, current.to());

		BigDecimal kwh = totalKwh(hours, current);
		BigDecimal prevKwh = totalKwh(hours, previous);

		List<Supply> supplyMix = supplyMix(hours, current);
		BigDecimal usd = supplyMix.isEmpty() ? null : sum(supplyMix.stream().map(Supply::usd).toList());

		Total total = new Total(
				kwh(kwh),
				kwh(prevKwh),
				changePct(kwh, prevKwh),
				usd,
				usd == null ? null : rate(usd, kwh)
		);

		return new PowerUsageResponseDto(
				period.label(),
				formatTs(current.from()),
				formatTs(current.to()),
				formatTs(previous.from()),
				formatTs(previous.to()),
				total,
				withForecast
						? forecast(hours, period, date, today, running, current, monthStart, prevMonthStart)
						: null,
				byFactory(hours, current, previous, kwh),
				byPurpose(hours, current, previous, last7First),
				true,
				supplyMix,
				last7(hours, last7First)
		);
	}

	// ---------------------------------------------------------------------
	// Data loading
	// ---------------------------------------------------------------------

	private Map<LocalDateTime, HourUsage> loadHours(LocalDateTime from, LocalDateTime to) {
		Map<LocalDateTime, HourUsage> hours = new HashMap<>();

		if (!from.isBefore(to)) {
			return hours;
		}

		// rows are stamped at the end of their hour
		List<PowerUsageHourProjection> rows = repo.findHourlyEnergy(
				ENERGY_NAME, AIR_CABINET_KEYS, from.plusHours(1), to.plusHours(1));

		for (PowerUsageHourProjection row : rows) {
			if (row.getHourAt() == null || row.getKwh() == null || row.getHourAt().isBefore(from)) {
				continue;
			}

			HourUsage usage = hours.computeIfAbsent(row.getHourAt(), key -> new HourUsage());
			String fac = row.getFac() == null ? "" : row.getFac().trim();

			usage.totalByFac.merge(fac, row.getKwh(), BigDecimal::add);

			if (row.getSolar() != null && row.getSolar() == 1) {
				usage.solar = usage.solar.add(row.getKwh());
			} else {
				usage.grid = usage.grid.add(row.getKwh());

				if (row.getPurposeKey() != null) {
					usage.gridByPurpose.merge(row.getPurposeKey(), row.getKwh(), BigDecimal::add);
				}
			}
		}

		return hours;
	}

	// tariff schedule (TouTariffService); empty when it is not usable or no
	// exchange rate is configured
	private Optional<TouTariff> loadTariff() {
		UtilityFinanceProperties.Policy policy = financeProperties.getPowerUsage();

		if (policy == null || policy.getExchangeRate() == null || policy.getExchangeRate().signum() == 0
				|| policy.getSepzone() == null) {
			return Optional.empty();
		}

		return tariffService.load();
	}

	// ---------------------------------------------------------------------
	// Sections
	// ---------------------------------------------------------------------

	private List<Supply> supplyMix(Map<LocalDateTime, HourUsage> hours, Window window) {
		Optional<TouTariff> tariff = loadTariff();

		if (tariff.isEmpty()) {
			return List.of();
		}

		Map<Band, BigDecimal> kwhByBand = new EnumMap<>(Band.class);
		Map<Band, BigDecimal> vndByBand = new EnumMap<>(Band.class);

		for (Band band : Band.values()) {
			kwhByBand.put(band, BigDecimal.ZERO);
			vndByBand.put(band, BigDecimal.ZERO);
		}

		hours.forEach((hourAt, usage) -> {
			if (!window.contains(hourAt)) {
				return;
			}

			kwhByBand.merge(Band.SOLAR, usage.solar, BigDecimal::add);

			for (TouShare share : tariff.get().shares(hourAt)) {
				BigDecimal gridPart = usage.grid.multiply(share.fraction());
				Band band = Band.valueOf(share.band().name());

				kwhByBand.merge(band, gridPart, BigDecimal::add);
				vndByBand.merge(band, gridPart.multiply(share.vnd()), BigDecimal::add);
				vndByBand.merge(Band.SOLAR,
						usage.solar.multiply(share.fraction()).multiply(share.vnd()).multiply(SOLAR_PRICE_FACTOR),
						BigDecimal::add);
			}
		});

		BigDecimal totalKwh = sum(kwhByBand.values().stream().toList());
		List<Supply> mix = new ArrayList<>(Band.values().length);

		for (Band band : Band.values()) {
			BigDecimal bandKwh = kwhByBand.get(band);
			BigDecimal bandUsd = toUsd(vndByBand.get(band));

			mix.add(new Supply(
					band.name(),
					kwh(bandKwh),
					sharePct(bandKwh, totalKwh),
					rate(bandUsd, bandKwh),
					usd(bandUsd)
			));
		}

		return mix;
	}

	private Forecast forecast(
			Map<LocalDateTime, HourUsage> hours,
			PowerUsagePeriod period,
			LocalDate date,
			LocalDate today,
			boolean running,
			Window current,
			LocalDateTime monthStart,
			LocalDateTime prevMonthStart
	) {
		LocalDateTime monthEnd = monthStart.plusMonths(1);
		Window monthToDate = new Window(monthStart, current.to());
		BigDecimal monthToDateKwh = totalKwh(hours, monthToDate);

		long elapsedHours = monthToDate.hours();
		long monthHours = ChronoUnit.HOURS.between(monthStart, monthEnd);

		BigDecimal projected;

		if (!monthToDate.to().isBefore(monthEnd)) {
			projected = monthToDateKwh;
		} else if (elapsedHours <= 0) {
			projected = null;
		} else {
			projected = monthToDateKwh
					.multiply(BigDecimal.valueOf(monthHours))
					.divide(BigDecimal.valueOf(elapsedHours), 6, RoundingMode.HALF_UP);
		}

		int daysInMonth = date.lengthOfMonth();
		int dayOfMonth = period == PowerUsagePeriod.DAY
				? date.getDayOfMonth()
				: running ? today.getDayOfMonth() : daysInMonth;

		BigDecimal prevMonthKwh = totalKwh(hours, new Window(prevMonthStart, monthStart));

		return new Forecast(
				kwh(monthToDateKwh),
				projected == null ? null : kwh(projected),
				dayOfMonth,
				daysInMonth,
				projected == null ? null : changePct(projected, prevMonthKwh)
		);
	}

	// kWh desc
	private List<Factory> byFactory(
			Map<LocalDateTime, HourUsage> hours,
			Window current,
			Window previous,
			BigDecimal totalKwh
	) {
		Map<String, BigDecimal> currentByFac = new TreeMap<>();
		Map<String, BigDecimal> previousByFac = new TreeMap<>();

		hours.forEach((hourAt, usage) -> {
			if (current.contains(hourAt)) {
				usage.totalByFac.forEach((fac, value) -> currentByFac.merge(fac, value, BigDecimal::add));
			}

			if (previous.contains(hourAt)) {
				usage.totalByFac.forEach((fac, value) -> previousByFac.merge(fac, value, BigDecimal::add));
			}
		});

		TreeSet<String> facs = new TreeSet<>(currentByFac.keySet());
		facs.addAll(previousByFac.keySet());

		return facs.stream()
				.map(fac -> {
					BigDecimal kwh = currentByFac.getOrDefault(fac, BigDecimal.ZERO);
					BigDecimal prevKwh = previousByFac.getOrDefault(fac, BigDecimal.ZERO);

					return new Factory(fac, kwh(kwh), kwh(prevKwh), sharePct(kwh, totalKwh), changePct(kwh, prevKwh));
				})
				.sorted(Comparator.comparing(Factory::kwh).reversed().thenComparing(Factory::facId))
				.toList();
	}

	// one item per compressed air cabinet, in AIR_CABINETS order (zeros when
	// the cabinet has no data)
	private List<Purpose> byPurpose(
			Map<LocalDateTime, HourUsage> hours,
			Window current,
			Window previous,
			LocalDate last7First
	) {
		return AIR_CABINETS.stream()
				.map(cabinet -> {
					Function<HourUsage, BigDecimal> value =
							usage -> usage.gridByPurpose.getOrDefault(cabinet.key(), BigDecimal.ZERO);

					BigDecimal kwh = sumKwh(hours, current, value);
					BigDecimal prevKwh = sumKwh(hours, previous, value);

					List<BigDecimal> last7 = new ArrayList<>(LAST_DAYS);

					for (int i = 0; i < LAST_DAYS; i++) {
						last7.add(kwh(sumKwh(hours, dayWindow(last7First.plusDays(i)), value)));
					}

					return new Purpose(
							cabinet.code(),
							"Compressed Air " + cabinet.boxId(),
							cabinet.fac() + " compressed air cabinet " + cabinet.boxId(),
							kwh(kwh),
							kwh(prevKwh),
							changePct(kwh, prevKwh),
							last7
					);
				})
				.toList();
	}

	private List<DayTotal> last7(Map<LocalDateTime, HourUsage> hours, LocalDate first) {
		List<DayTotal> days = new ArrayList<>(LAST_DAYS);

		for (int i = 0; i < LAST_DAYS; i++) {
			LocalDate day = first.plusDays(i);
			days.add(new DayTotal(day.toString(), kwh(totalKwh(hours, dayWindow(day)))));
		}

		return days;
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private Window dayWindow(LocalDate day) {
		return new Window(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
	}

	// grid + solar
	private BigDecimal totalKwh(Map<LocalDateTime, HourUsage> hours, Window window) {
		return sumKwh(hours, window, HourUsage::total);
	}

	private BigDecimal sumKwh(
			Map<LocalDateTime, HourUsage> hours,
			Window window,
			Function<HourUsage, BigDecimal> value
	) {
		BigDecimal total = BigDecimal.ZERO;

		for (Map.Entry<LocalDateTime, HourUsage> entry : hours.entrySet()) {
			if (window.contains(entry.getKey())) {
				total = total.add(value.apply(entry.getValue()));
			}
		}

		return total;
	}

	private BigDecimal toUsd(BigDecimal vnd) {
		UtilityFinanceProperties.Policy policy = financeProperties.getPowerUsage();

		return vnd
				.divide(policy.getExchangeRate(), 8, RoundingMode.HALF_UP)
				.multiply(policy.getSepzone());
	}

	// null when previous is 0
	private BigDecimal changePct(BigDecimal current, BigDecimal previous) {
		if (previous == null || previous.signum() == 0) {
			return null;
		}

		return current.subtract(previous)
				.multiply(HUNDRED)
				.divide(previous, 1, RoundingMode.HALF_UP);
	}

	private BigDecimal sharePct(BigDecimal part, BigDecimal total) {
		if (total == null || total.signum() == 0) {
			return null;
		}

		return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
	}

	// USD/kWh, null when kwh is 0
	private BigDecimal rate(BigDecimal usd, BigDecimal kwh) {
		if (kwh == null || kwh.signum() == 0) {
			return null;
		}

		return usd.divide(kwh, 4, RoundingMode.HALF_UP);
	}

	private BigDecimal kwh(BigDecimal value) {
		return value.setScale(1, RoundingMode.HALF_UP);
	}

	private BigDecimal usd(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal positive(BigDecimal value) {
		return value.signum() < 0 ? BigDecimal.ZERO : value;
	}

	private BigDecimal sum(List<BigDecimal> values) {
		return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private LocalDateTime min(LocalDateTime first, LocalDateTime second) {
		return first.isBefore(second) ? first : second;
	}

	private LocalDateTime max(LocalDateTime first, LocalDateTime second) {
		return first.isAfter(second) ? first : second;
	}

	private String formatTs(LocalDateTime value) {
		return value.atZone(APP_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}
}
