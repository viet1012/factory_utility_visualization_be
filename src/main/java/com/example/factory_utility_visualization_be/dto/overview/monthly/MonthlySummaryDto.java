package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MonthlySummaryDto(
		String cate,
		String name,
		String month,
		BigDecimal value,
		String unit,
		LocalDateTime timestamp
) {}
