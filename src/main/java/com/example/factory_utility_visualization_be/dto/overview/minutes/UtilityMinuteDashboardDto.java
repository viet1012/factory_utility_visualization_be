package com.example.factory_utility_visualization_be.dto.overview.minutes;


import java.time.LocalDateTime;
import java.util.List;

public record UtilityMinuteDashboardDto(
		String facId,
		Integer minutes,
		LocalDateTime generatedAt,
		List<OverviewMinutePointDto> electricity,
		List<OverviewMinutePointDto> water,
		List<OverviewMinutePointDto> air
) {
}