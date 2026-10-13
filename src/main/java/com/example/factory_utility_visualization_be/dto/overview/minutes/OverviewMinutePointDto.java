package com.example.factory_utility_visualization_be.dto.overview.minutes;


import java.time.LocalDateTime;

public record OverviewMinutePointDto(
		LocalDateTime ts,
		Double value,
		String nameEn
) {
}