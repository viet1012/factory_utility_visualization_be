package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import java.math.BigDecimal;

public record UtilitySignalWindowStats(
		long sampleCount,
		BigDecimal minValue,
		BigDecimal maxValue,
		BigDecimal avgValue,
		BigDecimal sumValue
) {
}
