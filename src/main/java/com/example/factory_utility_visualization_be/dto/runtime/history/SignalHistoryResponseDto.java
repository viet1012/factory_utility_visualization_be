package com.example.factory_utility_visualization_be.dto.runtime.history;

import java.util.List;

/**
 * source: "RAW" (F2_Utility_Para_History) | "HOURLY_SNAPSHOT" (F2_Utility_Para_History_Main)
 * aggregation: "MIN_AVG_MAX" | "SNAPSHOT"
 */
public record SignalHistoryResponseDto(
		String from,
		String to,
		String bucket,
		String source,
		String aggregation,
		List<SignalHistorySeriesDto> signals
) {}
