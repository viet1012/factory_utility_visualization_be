package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;

public record MonthlySummaryDto(
		String cate,
		String name,
		String month,
		BigDecimal value,
		String unit
) {}
