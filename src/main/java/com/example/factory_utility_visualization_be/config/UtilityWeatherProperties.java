package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for GET /api/utility/weather (utility.weather.*).
 *
 * apiKey comes from the OPENWEATHER_API_KEY environment variable; it must never
 * be logged or returned, so this class intentionally has no toString().
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.weather")
public class UtilityWeatherProperties {

	private String apiKey;

	// Default location when the request omits lat/lon
	private double lat = 10.889414655521932;
	private double lon = 106.72094061256969;
}
