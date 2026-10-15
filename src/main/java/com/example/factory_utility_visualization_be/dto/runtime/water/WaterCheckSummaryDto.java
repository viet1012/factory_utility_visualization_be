package com.example.factory_utility_visualization_be.dto.runtime.water;

// Signal count per status; each signal is counted once.
public record WaterCheckSummaryDto(
		long ok,
		long near,
		long over,
		long stale,
		long noThreshold
) {}
