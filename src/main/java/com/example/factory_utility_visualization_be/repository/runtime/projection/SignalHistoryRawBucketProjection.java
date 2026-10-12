package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SignalHistoryRawBucketProjection {
	String getPlcAddress();

	LocalDateTime getBucketAt();

	BigDecimal getMinValue();

	BigDecimal getAvgValue();

	BigDecimal getMaxValue();

	Long getSampleCount();

	// value of the latest sample in the bucket
	BigDecimal getLastValue();
}
