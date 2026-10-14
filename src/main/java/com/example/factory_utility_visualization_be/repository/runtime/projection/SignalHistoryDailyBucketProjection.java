package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SignalHistoryDailyBucketProjection {
	String getPlcAddress();

	LocalDateTime getBucketAt();

	BigDecimal getMinValue();

	BigDecimal getAvgValue();

	BigDecimal getMaxValue();

	BigDecimal getLastValue();

	Long getSampleCount();

	BigDecimal getPositiveSum();

	Long getPositiveCount();
}
