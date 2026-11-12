package com.example.factory_utility_visualization_be.dto.overview.hourly;


import java.time.LocalDateTime;
import java.util.List;

public record UtilityHourlyDashboardDto(
		String facId,

		LocalDateTime generatedAt,

		// Điện tiêu thụ, không bao gồm Solar
		// (solar theo giờ: GET /api/solar/hourly)
		List<HourlyCompareDto> electricity,

		// Water
		List<HourlyTempCompareDto> water,

		// Air
		List<HourlyTempCompareDto> air
) {
}