package com.example.factory_utility_visualization_be.dto.overview.hourly;


import java.time.LocalDateTime;
import java.util.List;

public record UtilityHourlyDashboardDto(
		String facId,
		LocalDateTime generatedAt,
		List<HourlyCompareDto> electricity,
		List<HourlyTempCompareDto> water,
		List<HourlyTempCompareDto> air
) {
}