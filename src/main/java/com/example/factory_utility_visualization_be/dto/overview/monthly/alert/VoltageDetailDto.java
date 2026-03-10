package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import java.time.LocalDateTime;

public record VoltageDetailDto(
		LocalDateTime pickAt,

		Double d12,
		Double d14,
		Double d16,

		String alarm
) {
}
