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

		/**
		 * Electricity: điện tiêu thụ đã loại Solar.
		 * Water/Air: null.
		 */
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

		/**
		 * Điện Solar phát trong kỳ hiện tại.
		 * Chỉ có giá trị ở dòng Electricity.
		 */
		BigDecimal solarValue,

		/**
		 * Điện Solar của kỳ trước.
		 */
		BigDecimal prevSolarValue,

		/**
		 * value + solarValue.
		 */
		BigDecimal totalEnergyValue,

		/**
		 * prevValue + prevSolarValue.
		 */
		BigDecimal prevTotalEnergyValue,

		/**
		 * solarValue / totalEnergyValue * 100.
		 */
		BigDecimal solarSharePercent,

		OffsetDateTime serverTime

) {
}