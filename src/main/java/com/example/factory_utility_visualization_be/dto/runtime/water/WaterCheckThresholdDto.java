package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.math.BigDecimal;

/**
 * One active THRESHOLD rule of the group's code, same rules as
 * /series/history. trivial = low is 0, no high limit and no current value
 * in the group is negative; trivial rules are ignored for status.
 */
public record WaterCheckThresholdDto(
		Integer level,
		BigDecimal low,
		BigDecimal high,
		boolean trivial
) {}
