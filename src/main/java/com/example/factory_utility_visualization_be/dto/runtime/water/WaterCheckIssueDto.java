package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.math.BigDecimal;

// thresholdText: "0.2 - 0.6 bar", ">= 0.2 bar", "<= 0.6 bar"; null without thresholds
public record WaterCheckIssueDto(
		String facId,
		String boxDeviceId,
		String plcAddress,
		String name,
		String unit,
		// value / medianValue / recordedAt: see WaterCheckSignalDto
		BigDecimal value,
		BigDecimal medianValue,
		String recordedAt,
		// last known reading, see WaterCheckSignalDto
		BigDecimal lastValue,
		String lastSeenAt,
		WaterCheckStatus status,
		WaterCheckStatusBasis statusBasis,
		// see WaterCheckSignalDto
		String reason,
		String thresholdText,
		String since,
		BigDecimal trend1h
) {}
