package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PowerUsageTariffProjection {
	String getWd();

	BigDecimal getFrTime();

	BigDecimal getToTime();

	BigDecimal getVnd();

	// effective_from (inclusive) at 00:00, null = no lower bound
	LocalDateTime getEffectiveFrom();

	// effective_to (exclusive) at 00:00, null = still in effect
	LocalDateTime getEffectiveTo();
}
