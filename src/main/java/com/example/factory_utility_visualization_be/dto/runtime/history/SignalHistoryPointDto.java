package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.math.BigDecimal;

/**
 * One bucket. ts is ISO-8601 with offset (+07:00).
 * Empty bucket: min/avg/max/last/delta = null, count = 0.
 * last = value of the latest sample in the bucket (all kinds).
 * HOURLY_SNAPSHOT: avg = last = snapshot value, min = max = null.
 * delta is only set for COUNTER signals.
 */
public record SignalHistoryPointDto(
		String ts,
		BigDecimal min,
		BigDecimal avg,
		BigDecimal max,
		BigDecimal last,
		long count,
		BigDecimal delta
) {}
