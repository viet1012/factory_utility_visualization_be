package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.math.BigDecimal;
import java.util.List;

/**
 * thresholdLow / thresholdHigh / alertLevel: one effective limit per side
 * (highest AlertLevel), unchanged.
 * thresholds: every active THRESHOLD rule of the code, level descending.
 * breachCount: buckets whose max > high or min < low for any non-trivial
 * rule (HOURLY_SNAPSHOT has no min/max, avg is used instead). Always
 * checked on readings, never on delta.
 * lastBreachAt: ts of the latest such bucket, or null.
 */
public record SignalHistorySeriesDto(
		String boxDeviceId,
		String plcAddress,
		String name,
		String code,
		String unit,
		SignalKind kind,
		BigDecimal thresholdLow,
		BigDecimal thresholdHigh,
		Integer alertLevel,
		List<SignalThresholdDto> thresholds,
		int breachCount,
		String lastBreachAt,
		List<SignalHistoryPointDto> points
) {}
