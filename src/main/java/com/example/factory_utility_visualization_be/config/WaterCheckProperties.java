package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Settings for GET /api/utility/water-check (utility.water-check.*).
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.water-check")
public class WaterCheckProperties {

	// Pressure readings in [pumpStoppedMin, pumpStoppedMax] = pump stopped;
	// below pumpStoppedMin = sensor fault.
	private BigDecimal pumpStoppedMin = new BigDecimal("-0.3");
	private BigDecimal pumpStoppedMax = new BigDecimal("0.2");

	// Level signals (unit m / code W_LevCool): value, OVER / NEAR confirmation
	// and trend1h use the median of this many 1-minute buckets (min 1).
	private int levelMedianMinutes = 10;
}
