package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.math.BigDecimal;
import java.util.List;

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
		List<SignalHistoryPointDto> points
) {}
