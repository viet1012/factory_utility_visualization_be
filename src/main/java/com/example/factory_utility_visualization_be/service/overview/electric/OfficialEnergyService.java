package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.EvnDay;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.HourSum;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.Totals;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Money;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * THE official grid rule of every electricity view, per (day, scope):
 * F2_Utility_Evn_Daily row -> EVN, else the measured grid (GridSolarSplit);
 * never the calibrated estimate.
 *
 * EVN money (bandMoney / bandVnd) is used by ElectricConsumptionCalculator
 * (official of /electric-consumption) and by the dashboards (dailyOfficial):
 * band kWh x grid price of that band on that day (same tariff and factor as
 * the measured grid).
 *
 * Dashboard fac -> EVN scope (evnScope): KVH -> KVH, Fac_C -> FAC_C; Fac_A /
 * Fac_B have no EVN row of their own (EVN only has FAC_AB), so they are
 * always MEASURED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialEnergyService {

	public static final String EVN = "EVN";
	public static final String MEASURED = "MEASURED";

	private static final int HOURS_PER_DAY = 24;

	private final EvnCalibrationJdbcRepository evnRepo;

	/**
	 * Official grid of one day.
	 *
	 * @param vnd / usd grid money, null = not priced (no tariff version that day)
	 * @param priceMissing some energy of the day has no price
	 */
	public record OfficialDay(
			LocalDate day,
			BigDecimal gridKwh,
			BigDecimal vnd,
			BigDecimal usd,
			boolean priceMissing,
			// EVN | MEASURED
			String source
	) {}

	/** Sum of OfficialDay over a period; vnd / usd = sum of the priced days. */
	public record OfficialTotal(
			BigDecimal gridKwh,
			BigDecimal vnd,
			BigDecimal usd,
			boolean priceMissing,
			int evnDays,
			int measuredDays
	) {}

	// ---------------------------------------------------------------------
	// EVN pricing (shared with ElectricConsumptionCalculator)
	// ---------------------------------------------------------------------

	/**
	 * Grid VND/kWh per band on day: from the day's hourly tariff shares (so a
	 * Sunday rate is used on Sunday), a band missing that day -> weekday rate.
	 */
	public static Map<TouBand, BigDecimal> bandVnd(Pricing p, LocalDate day) {
		Map<TouBand, BigDecimal> vnd = new EnumMap<>(TouBand.class);

		for (int h = 0; h < HOURS_PER_DAY; h++) {
			for (TouShare share : p.shares(day.atStartOfDay().plusHours(h))) {
				vnd.putIfAbsent(share.band(), share.vnd());
			}
		}

		for (TouBand band : TouBand.values()) {
			if (!vnd.containsKey(band)) {
				p.weekdayVnd(band, day).ifPresent(rate -> vnd.put(band, rate));
			}
		}

		return vnd;
	}

	/**
	 * VND / USD per band of an EVN day: band kWh x bandVnd x grid factor.
	 * Empty when the day has no tariff version; a band without a price on
	 * that day is left out (logged when it has kWh).
	 */
	public static Map<TouBand, Money> bandMoney(EvnDay row, Pricing p) {
		Map<TouBand, Money> money = new EnumMap<>(TouBand.class);

		if (!p.priced(row.day())) {
			return money;
		}

		Map<TouBand, BigDecimal> vnd = bandVnd(p, row.day());

		for (Map.Entry<TouBand, BigDecimal> band : bandKwh(row).entrySet()) {
			BigDecimal rate = vnd.get(band.getKey());

			if (rate != null) {
				BigDecimal bandVnd = band.getValue().multiply(rate);
				money.put(band.getKey(), new Money(bandVnd, bandVnd.multiply(p.gridFactor())));
			} else if (band.getValue().signum() > 0) {
				log.warn("EVN {} {}: no {} price on that day, {} kWh not priced",
						row.scope(), row.day(), band.getKey(), band.getValue());
			}
		}

		return money;
	}

	public static Map<TouBand, BigDecimal> bandKwh(EvnDay row) {
		Map<TouBand, BigDecimal> kwh = new EnumMap<>(TouBand.class);
		kwh.put(TouBand.NORMAL, row.normalKwh());
		kwh.put(TouBand.HIGH_PEAK, row.highKwh());
		kwh.put(TouBand.LOW_PEAK, row.lowKwh());
		return kwh;
	}

	// ---------------------------------------------------------------------
	// Dashboards (monthly-summary, energy-daily, solar/monthly)
	// ---------------------------------------------------------------------

	/** Dashboard fac -> F2_Utility_Evn_Daily scope, null = no EVN row (Fac_A / Fac_B). */
	public static String evnScope(String fac) {
		String key = GridSolarSplit.facKey(fac);

		return switch (key) {
			case GridSolarSplit.KVH -> "KVH";
			case "FAC_C" -> "FAC_C";
			default -> null;
		};
	}

	/**
	 * EVN rows of [from, to) of the scope of fac; none when the fac has no
	 * EVN scope or the table is missing / unreadable (views keep working).
	 */
	public Map<LocalDate, EvnDay> evnDays(String fac, LocalDate from, LocalDate to) {
		String scope = evnScope(fac);

		if (scope == null || !from.isBefore(to)) {
			return Map.of();
		}

		try {
			Map<LocalDate, EvnDay> rows = new HashMap<>();

			for (EvnDay row : evnRepo.findEvnDays(from, to, scope)) {
				rows.put(row.day(), row);
			}

			return rows;
		} catch (DataAccessException e) {
			log.warn("F2_Utility_Evn_Daily not readable, official = measured: {}", e.getMessage());
			return Map.of();
		}
	}

	/**
	 * Official grid per day of [from, to) (hour start, hourOffset applied as
	 * in hours): EVN day -> EVN total kWh + bandMoney (the whole day, even when
	 * [from, to) cuts it); else the measured grid / money of the hours of that
	 * day inside [from, to) (GridSolarSplit.totals), only days with data.
	 * Sorted by day.
	 */
	public List<OfficialDay> dailyOfficial(
			Map<LocalDateTime, HourSum> hours,
			LocalDateTime from,
			LocalDateTime to,
			Map<LocalDate, EvnDay> evn,
			Optional<Pricing> pricing
	) {
		// measured hours per day (inside [from, to))
		Map<LocalDate, Map<LocalDateTime, HourSum>> byDay = new TreeMap<>();

		hours.forEach((hourAt, sum) -> {
			if (!hourAt.isBefore(from) && hourAt.isBefore(to)) {
				byDay.computeIfAbsent(hourAt.toLocalDate(), key -> new TreeMap<>()).put(hourAt, sum);
			}
		});

		List<OfficialDay> days = new ArrayList<>();

		for (LocalDate day = from.toLocalDate(); day.atStartOfDay().isBefore(to); day = day.plusDays(1)) {
			EvnDay row = evn.get(day);

			if (row != null) {
				days.add(evnDay(row, pricing));
				continue;
			}

			Map<LocalDateTime, HourSum> dayHours = byDay.get(day);

			if (dayHours == null) {
				continue;
			}

			Totals t = GridSolarSplit.totals(dayHours, from, to, pricing);

			if (!t.hasData()) {
				continue;
			}

			boolean priced = pricing.isPresent() && pricing.get().priced(day);

			days.add(new OfficialDay(day, t.grid(),
					priced ? t.gridVnd() : null,
					priced ? t.gridUsd() : null,
					pricing.isEmpty() || t.priceMissing(),
					MEASURED));
		}

		return days;
	}

	public static OfficialTotal total(List<OfficialDay> days) {
		BigDecimal kwh = BigDecimal.ZERO;
		BigDecimal vnd = BigDecimal.ZERO;
		BigDecimal usd = BigDecimal.ZERO;
		boolean priceMissing = false;
		int evnDays = 0;

		for (OfficialDay day : days) {
			kwh = kwh.add(day.gridKwh());

			if (day.vnd() != null) {
				vnd = vnd.add(day.vnd());
				usd = usd.add(day.usd());
			}

			priceMissing |= day.priceMissing();

			if (EVN.equals(day.source())) {
				evnDays++;
			}
		}

		return new OfficialTotal(kwh, vnd, usd, priceMissing, evnDays, days.size() - evnDays);
	}

	private static OfficialDay evnDay(EvnDay row, Optional<Pricing> pricing) {
		if (pricing.isEmpty() || !pricing.get().priced(row.day())) {
			return new OfficialDay(row.day(), row.totalKwh(), null, null, row.totalKwh().signum() > 0, EVN);
		}

		Map<TouBand, Money> money = bandMoney(row, pricing.get());
		BigDecimal vnd = BigDecimal.ZERO;
		BigDecimal usd = BigDecimal.ZERO;

		for (Money m : money.values()) {
			vnd = vnd.add(m.vnd());
			usd = usd.add(m.usd());
		}

		// a band with kWh but no price on that day
		boolean missing = bandKwh(row).entrySet().stream()
				.anyMatch(band -> band.getValue().signum() > 0 && !money.containsKey(band.getKey()));

		return new OfficialDay(row.day(), row.totalKwh(), vnd, usd, missing, EVN);
	}

	// for log lines: "KVH 12 EVN + 4 MEASURED"
	public static String describe(String fac, OfficialTotal total) {
		return String.format(Locale.ROOT, "%s %d EVN + %d MEASURED", fac, total.evnDays(), total.measuredDays());
	}
}
