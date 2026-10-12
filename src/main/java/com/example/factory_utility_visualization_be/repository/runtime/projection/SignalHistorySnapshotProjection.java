package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SignalHistorySnapshotProjection {
	String getPlcAddress();

	LocalDateTime getBucketAt();

	BigDecimal getValue();
}
