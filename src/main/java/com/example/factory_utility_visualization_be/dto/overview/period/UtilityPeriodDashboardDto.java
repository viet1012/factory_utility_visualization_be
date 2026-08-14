package com.example.factory_utility_visualization_be.dto.overview.period;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UtilityPeriodDashboardDto(

		String facId,

		String utilityType,

		String period,

		LocalDate fromDate,

		LocalDate toDate,

		String unit,

		LocalDateTime generatedAt,

		// =====================================================
		// CURRENT PERIOD VS PREVIOUS SAME PROGRESS
		// =====================================================

		BigDecimal total,

		BigDecimal previousTotal,

		BigDecimal changePercent,

		// =====================================================
		// TREND
		// =====================================================

		List<UtilityPeriodTrendDto> trend,

		// =====================================================
		// BY BOX
		// =====================================================

		List<UtilityPeriodBoxDto> byBox,

		// =====================================================
		// HEATMAP
		// =====================================================

		List<String> columns,

		List<UtilityPeriodBoxTrendDto> boxTrend,

		List<BigDecimal> columnTotals,

		BigDecimal grandTotal

) {
}