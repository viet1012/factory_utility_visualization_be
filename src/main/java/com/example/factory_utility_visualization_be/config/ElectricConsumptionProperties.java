package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/utility/electric-consumption, same rules as the
 * "F2 electric consumption.xlsx" daily monitoring sheets:
 * - bill period of month M = [day billingCutoffDay of M-1, day billingCutoffDay of M)
 *   (e.g. Sep = Aug 21 ~ Sep 20), labelled with the month the period ENDS in
 * - prices and excluded meters: ElectricityProperties (utility.electricity),
 *   shared with every other electricity endpoint
 * - a day counts in averages only when it is over and its meter-hour
 *   coverage is >= minCoveragePct
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.electric-consumption")
public class ElectricConsumptionProperties {

	private int billingCutoffDay = 21;

	// data before this day is not used (meters were still being installed, so
	// totals are far too low); null = use all data
	private LocalDate historyStartDate;

	private int defaultMonths = 12;

	private int maxMonths = 36;

	// a finished day is complete when meter-hours with grid data >=
	// minCoveragePct % of (expected meters x 24); expected meters = configured
	// grid meters of the scope for the selected period
	private BigDecimal minCoveragePct = BigDecimal.valueOf(98);

	// monthly history only: expected meters = most grid meters seen in one
	// hour within +/- meterWindowDays of that day
	private int meterWindowDays = 3;

	// meter-hour dropped when kWh > outlierMinKwh AND kWh > outlierRatio x
	// median kWh/hour of that meter (cumulative / catch-up values)
	private BigDecimal outlierRatio = BigDecimal.TEN;

	private BigDecimal outlierMinKwh = BigDecimal.valueOf(100);

	// SOLAR meter-hour dropped when kWh > outlierMinKwh AND kWh >
	// solarOutlierRatio x P90 kWh/hour of that meter
	private BigDecimal solarOutlierRatio = BigDecimal.valueOf(3);

	// box_device_id -> scope by transformer, overriding the SCADA fac
	private MeterScope meterScope = new MeterScope();

	@Getter
	@Setter
	public static class MeterScope {
		private List<String> facAb = new ArrayList<>();
		private List<String> facC = new ArrayList<>();
	}
}
