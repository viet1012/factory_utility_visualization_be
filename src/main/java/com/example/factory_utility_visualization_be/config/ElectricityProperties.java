package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Single electricity pricing / meter policy for EVERY endpoint that shows
 * money or kWh of electricity (hourly-dashboard, monthly-summary,
 * solar/detail, power-usage, electric-consumption), so they always agree.
 * Rules follow "F2 electric consumption.xlsx":
 *
 * - VND/kWh per hour: time-of-use tariff of F2_Utility_Cost_Master (the only
 *   rate source; see TouTariffService)
 * - grid  $ = kWh x VND x grid.sepzone / grid.exchangeRate       (sheet C40)
 * - solar $ = kWh x VND x solarPriceFactor[band]
 *               x solar.sepzone / solar.exchangeRate              (sheet JI36/JI37)
 * - excludedMeters: box_device_id never counted (duplicate meters)
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.electricity")
public class ElectricityProperties {

	private Policy grid = new Policy();

	private Policy solar = new Policy();

	private SolarPriceFactor solarPriceFactor = new SolarPriceFactor();

	// box_device_id not counted at all (duplicate meters, e.g. P-3.1 = P-3)
	private List<String> excludedMeters = new ArrayList<>();

	@Getter
	@Setter
	public static class Policy {
		private BigDecimal exchangeRate;
		private BigDecimal sepzone;
	}

	// share of the grid VND/kWh paid for solar, per tariff band
	@Getter
	@Setter
	public static class SolarPriceFactor {
		private BigDecimal normal = new BigDecimal("0.9");
		private BigDecimal highPeak = new BigDecimal("0.9");
		private BigDecimal lowPeak = BigDecimal.ZERO;
	}
}
