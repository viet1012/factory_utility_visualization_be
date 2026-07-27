package com.example.factory_utility_visualization_be.dto.overview.daily;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record UtilityDailyEnergyCostResponse(
		String facId,
		String month,
		LocalDateTime fromTime,
		LocalDateTime toTime,
		List<DailyEnergyCostPointDto> points
) {
}