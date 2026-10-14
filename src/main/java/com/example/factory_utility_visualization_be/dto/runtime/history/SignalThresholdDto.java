package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.math.BigDecimal;

/**
 * One active THRESHOLD rule of the signal's code (F2_Utility_Alert_Master).
 * trivial = low is 0, there is no high limit and the signal's data in the
 * response is never negative, so the rule cannot fire; trivial rules are
 * not counted in breachCount.
 */
public record SignalThresholdDto(
		Integer level,
		BigDecimal low,
		BigDecimal high,
		String ruleName,
		String description,
		boolean trivial
) {}
