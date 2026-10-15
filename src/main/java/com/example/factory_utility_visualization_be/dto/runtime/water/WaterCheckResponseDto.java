package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.util.List;

/**
 * GET /api/utility/water-check. Timestamps are ISO-8601 with the plant
 * offset (+07:00), same format as /series/history.
 */
public record WaterCheckResponseDto(
		String generatedAt,
		WaterCheckSummaryDto summary,
		List<WaterCheckFacilityDto> facilities,
		// OVER, NEAR, STALE signals: severity first, then oldest since first
		List<WaterCheckIssueDto> issues
) {}
