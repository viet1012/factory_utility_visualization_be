package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for GET /api/utility/series/history (utility.history.*).
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.history")
public class UtilityHistoryProperties {

	// Raw keeps ~24h and is purged continuously, so "now - 24h" is usually a
	// few minutes older than the oldest raw sample. from within this many
	// minutes before the oldest raw sample still counts as covered by raw.
	private long rawToleranceMinutes = 60;
}
