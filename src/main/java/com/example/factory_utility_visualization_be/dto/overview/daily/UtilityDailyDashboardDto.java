package com.example.factory_utility_visualization_be.dto.overview.daily;

import lombok.Builder;

import java.util.List;

@Builder
public record UtilityDailyDashboardDto(

		String facId,

		String month,

		List<DailyElectricityStackDto> electricity,

		List<DailyDto> water,

		List<DailyDto> air

) {
}