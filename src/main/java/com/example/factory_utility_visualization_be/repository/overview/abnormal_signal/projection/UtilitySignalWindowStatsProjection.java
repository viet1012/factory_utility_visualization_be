package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection;

import java.math.BigDecimal;

public interface UtilitySignalWindowStatsProjection {
	Long getParaId();
	Long getSampleCount();
	BigDecimal getWindowMinValue();
	BigDecimal getWindowMaxValue();
	BigDecimal getWindowAvgValue();
	BigDecimal getWindowSumValue();
}
