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
 * - grid U.P [$/kWh] = tariff VND * grid.sepzone / grid.exchangeRate (sheet C40..C42)
 * - solar U.P [$/kWh] = tariff VND * solarPriceFactor[band] * solar.sepzone / solar.exchangeRate
 *   (sheet JI36 = 1649.7 / 26316, JI37 = 3058.2 / 26316, low peak = 0)
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
	// minCoveragePct % of (expected meters x 24); expected meters = most grid
	// meters seen in one hour within +/- meterWindowDays of that day, so meters
	// added mid-period do not make the earlier days partial
	private BigDecimal minCoveragePct = BigDecimal.valueOf(98);

	private int meterWindowDays = 3;

	// hours added to History_Main hourAt (pick_at - 1h) before the day / tariff
	// band is chosen; for testing the hour stamp against the EVN TOU registers
	private int hourOffset = 0;

	// meter-hour dropped when kWh > outlierMinKwh AND kWh > outlierRatio x
	// median kWh/hour of that meter (cumulative / catch-up values)
	private BigDecimal outlierRatio = BigDecimal.TEN;

	private BigDecimal outlierMinKwh = BigDecimal.valueOf(100);

	// SOLAR meter-hour dropped when kWh > outlierMinKwh AND kWh >
	// solarOutlierRatio x P90 kWh/hour of that meter
	private BigDecimal solarOutlierRatio = BigDecimal.valueOf(3);

	// box_device_id not counted at all (duplicate meters)
	private List<String> excludedMeters = new ArrayList<>();

	// box_device_id -> scope by transformer, overriding the SCADA fac
	private MeterScope meterScope = new MeterScope();

	@Getter
	@Setter
	public static class MeterScope {
		private List<String> facAb = new ArrayList<>();
		private List<String> facC = new ArrayList<>();
	}

	private SolarPriceFactor solarPriceFactor = new SolarPriceFactor();

	// VND/kWh per band like the sheet (C40..C42: 1833 / 3398 / 1190); each
	// value left empty falls back to the rate of F2_Utility_Cost_Master.
	// Band time windows always come from F2_Utility_Cost_Master.
	private BandPrices bandPrices = new BandPrices();

	private UtilityFinanceProperties.Policy grid = new UtilityFinanceProperties.Policy();

	private UtilityFinanceProperties.Policy solar = new UtilityFinanceProperties.Policy();

	@Getter
	@Setter
	public static class BandPrices {
		private BigDecimal normal;
		private BigDecimal highPeak;
		private BigDecimal lowPeak;
	}

	@Getter
	@Setter
	public static class SolarPriceFactor {
		private BigDecimal normal = new BigDecimal("0.9");
		private BigDecimal highPeak = new BigDecimal("0.9");
		private BigDecimal lowPeak = BigDecimal.ZERO;
	}
}
