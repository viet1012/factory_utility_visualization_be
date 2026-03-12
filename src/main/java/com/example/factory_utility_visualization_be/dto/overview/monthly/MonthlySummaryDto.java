package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record MonthlySummaryDto(
		String cate,
		String name,
		String month,
		BigDecimal value,
		String unit,
		LocalDateTime pick_at,
		OffsetDateTime timestamp

) {}
