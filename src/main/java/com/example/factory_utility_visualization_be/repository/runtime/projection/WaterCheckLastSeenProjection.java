package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface WaterCheckLastSeenProjection {
	String getBoxDeviceId();

	String getPlcAddress();

	LocalDateTime getPickAt();

	// cur_value of the latest snapshot, value when cur_value is NULL
	BigDecimal getLastValue();
}
