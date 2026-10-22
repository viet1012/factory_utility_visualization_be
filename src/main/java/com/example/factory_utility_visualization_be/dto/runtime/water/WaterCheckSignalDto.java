package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.math.BigDecimal;

/**
 * distance = distance of value to the nearest non-trivial limit (negative
 *            when over), null without thresholds or value
 * trend1h  = value - value 60 minutes ago (level signals with a median:
 *            medianValue - median then; null when either is missing)
 * reason   = "Pump stopped" (STOPPED), "Negative reading: sensor fault"
 *            (SENSOR_FAULT), null otherwise
 * since    = start of the current OVER / NEAR / SENSOR_FAULT / STALE state,
 *            null otherwise
 * value / recordedAt = latest raw sample of the last 24h (null when none)
 * medianValue = level signals (unit m / W_LevCool): median of the 1-minute
 *            bucket last values of the last levelMedianMinutes (see
 *            WaterCheckResponseDto); null for other signals or no bucket
 * status   = evaluated on medianValue when there is one, else on value;
 *            OVER / NEAR only once the last 3 1-minute buckets all reach it
 *            (rolling median per bucket when on medianValue), see statusBasis
 * lastValue / lastSeenAt = last known reading: the raw sample, else the
 *            latest F2_Utility_Para_History_Main snapshot (null when none)
 */
public record WaterCheckSignalDto(
		String boxDeviceId,
		String deviceName,
		String plcAddress,
		String name,
		BigDecimal value,
		BigDecimal medianValue,
		String recordedAt,
		BigDecimal lastValue,
		String lastSeenAt,
		WaterCheckStatus status,
		WaterCheckStatusBasis statusBasis,
		String reason,
		BigDecimal distance,
		BigDecimal trend1h,
		String since
) {}
