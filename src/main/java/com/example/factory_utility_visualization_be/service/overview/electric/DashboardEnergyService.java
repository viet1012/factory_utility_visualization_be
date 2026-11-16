package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.HourSum;
import com.example.factory_utility_visualization_be.service.overview.electric.HourlyEnergyCache.MeterHour;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * load / solar per hour of one dashboard fac (monthly-summary, solar/monthly,
 * energy-daily), same result as GridSolarSplit.hourSums over the same window
 * (findHourlyEnergy), but built from HourlyEnergyCache instead of one SQL per
 * request:
 *
 * 1. meter-hours of the window from the cached months (shared by KVH and
 *    every fac; SQL only when a month is missing or stale)
 * 2. outlier stats: median (grid) / P90 (SOLAR) kWh per hour of each meter
 *    over the SAME window as before (the SQL computed PERCENTILE_CONT over
 *    the query window), now in memory - a few ms instead of the SQL window
 *    functions; a longer / cached window would flag other hours and change
 *    the totals
 * 3. outlier flag (GridSolarSplit.isOutlier) and sum per hour into HourSum
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardEnergyService {

	private final HourlyEnergyCache hourlyCache;
	private final ElectricityPricingService pricingService;
	private final ElectricConsumptionProperties properties;

	private record Stats(BigDecimal median, BigDecimal p90) {}

	/**
	 * load / solar per hour start (hourOffset applied) in [from, to) of fac:
	 * "KVH" = every meter, otherwise meters whose fac key = facKey(fac).
	 */
	public Map<LocalDateTime, HourSum> facHours(String fac, LocalDateTime from, LocalDateTime to) {
		long started = System.nanoTime();
		int offset = pricingService.hourOffset();
		String facKey = GridSolarSplit.facKey(fac);

		// 1) cached meter-hours of the window (hourAt + offset in [from, to))
		List<MeterHour> rows = hourlyCache.rows(from.minusHours(offset), to.minusHours(offset));
		long hourlyNs = System.nanoTime();

		// 2) median / P90 per meter of this fac over the window
		Map<String, List<BigDecimal>> byMeter = new HashMap<>();
		Map<String, Boolean> solarMeter = new HashMap<>();

		for (MeterHour row : rows) {
			if (GridSolarSplit.facIncludes(facKey, row.fac())) {
				byMeter.computeIfAbsent(row.meter(), key -> new ArrayList<>()).add(row.kwh());
				solarMeter.putIfAbsent(row.meter(), row.solar());
			}
		}

		Map<String, Stats> stats = new HashMap<>(byMeter.size() * 2);

		byMeter.forEach((meter, values) -> {
			Collections.sort(values);
			boolean solar = solarMeter.get(meter);
			stats.put(meter, new Stats(
					solar ? null : GridSolarSplit.percentile(values, 0.5),
					solar ? GridSolarSplit.percentile(values, 0.9) : null));
		});
		long statsNs = System.nanoTime();

		// 3) outlier flag + sum per hour
		Map<LocalDateTime, HourSum> hours = new TreeMap<>();

		for (MeterHour row : rows) {
			if (!GridSolarSplit.facIncludes(facKey, row.fac())) {
				continue;
			}

			Stats s = stats.get(row.meter());
			boolean outlier = GridSolarSplit.isOutlier(row.kwh(), row.solar(), s.median(), s.p90(), properties);

			hours.computeIfAbsent(row.hourAt().plusHours(offset), key -> new HourSum())
					.add(row.kwh(), row.solar(), outlier, 1);
		}

		long done = System.nanoTime();

		log.info("dashboard energy {} [{} , {}): {} meter-hours, hourly {} ms, outlier stats {} ms, aggregate {} ms, total {} ms",
				facKey, from, to, rows.size(),
				(hourlyNs - started) / 1_000_000, (statsNs - hourlyNs) / 1_000_000,
				(done - statsNs) / 1_000_000, (done - started) / 1_000_000);

		return hours;
	}
}
