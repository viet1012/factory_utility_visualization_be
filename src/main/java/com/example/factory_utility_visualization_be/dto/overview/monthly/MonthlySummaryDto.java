package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record MonthlySummaryDto(
		String cate,
		String name,
		String month,

		BigDecimal minValue,
		BigDecimal maxValue,
		BigDecimal prevMinValue,
		BigDecimal prevMaxValue,

		BigDecimal value,
		BigDecimal avgValue,
		BigDecimal vndCost,
		BigDecimal usdCost,
		BigDecimal prevValue,
		BigDecimal prevAvgValue,
		BigDecimal prevVndCost,
		BigDecimal prevUsdCost,
		BigDecimal deltaValue,
		BigDecimal deltaPercent,
		String unit,
		LocalDateTime pickAt,
		OffsetDateTime serverTime
) {}