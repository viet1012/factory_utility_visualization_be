package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.math.BigDecimal;

/**
 * distance = distance to the nearest non-trivial limit (negative when over),
 *            null without thresholds or value
 * trend1h  = value - value 60 minutes ago (null when either is missing)
 * reason   = "Pump stopped" (STOPPED), "Negative reading: sensor fault"
 *            (SENSOR_FAULT), null otherwise
 * since    = start of the current OVER / NEAR / SENSOR_FAULT / STALE state,
 *            null otherwise
 * value / recordedAt = latest raw sample of the last 24h (null when none)
 * lastValue / lastSeenAt = last known reading: the raw sample, else the
 *            latest F2_Utility_Para_History_Main snapshot (null when none)
 */
public record WaterCheckSignalDto(
		String boxDeviceId,
		String deviceName,
		String plcAddress,
		String name,
		BigDecimal value,
		String recordedAt,
		BigDecimal lastValue,
		String lastSeenAt,
		WaterCheckStatus status,
		String reason,
		BigDecimal distance,
		BigDecimal trend1h,
		String since
) {}
