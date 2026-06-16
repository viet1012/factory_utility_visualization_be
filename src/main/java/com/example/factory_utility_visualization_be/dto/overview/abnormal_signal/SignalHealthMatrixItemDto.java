package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;


import java.time.LocalDateTime;

public record SignalHealthMatrixItemDto(
		String signalName,
		String plcAddress,
		Double currentValue,
		Double prevValue,
		Double jumpSize,
		String status,
		String description,
		LocalDateTime recordedAt
) {
}