package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SignalHistorySnapshotProjection {
	String getPlcAddress();

	LocalDateTime getBucketAt();

	// reading: COALESCE(cur_value, value)
	BigDecimal getValue();

	// raw History_Main.value: consumption of the hour ending at pick_at for COUNTER
	BigDecimal getConsumption();
}
