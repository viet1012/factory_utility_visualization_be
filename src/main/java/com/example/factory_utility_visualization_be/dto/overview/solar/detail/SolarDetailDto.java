package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.time.LocalDateTime;
import java.util.List;

public record SolarDetailDto(

		String facId,

		String month,

		LocalDateTime generatedAt,

		SolarDetailSummaryDto summary,

		List<SolarDailyTrendDto> dailyTrend,

		SolarCostImpactDto costImpact,

		SolarEnvironmentalDto environmentalImpact,

		SolarHourlyProfileDto hourlyProfile

) {
}