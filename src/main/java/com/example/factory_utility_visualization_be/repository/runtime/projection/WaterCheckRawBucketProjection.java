package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface WaterCheckRawBucketProjection {
	String getBoxDeviceId();

	String getPlcAddress();

	LocalDateTime getBucketAt();

	// value of the latest sample in the 1-minute bucket
	BigDecimal getLastValue();
}
