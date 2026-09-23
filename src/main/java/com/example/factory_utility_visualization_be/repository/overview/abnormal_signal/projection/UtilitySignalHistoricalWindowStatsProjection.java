package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtilitySignalHistoricalWindowStatsProjection {

	String getBoxDeviceId();

	String getPlcAddress();

	LocalDateTime getBucketAt();

	Integer getWindowMinutes();

	Long getSampleCount();

	BigDecimal getMinValue();

	BigDecimal getMaxValue();

	BigDecimal getAvgValue();

	BigDecimal getSumValue();
}
