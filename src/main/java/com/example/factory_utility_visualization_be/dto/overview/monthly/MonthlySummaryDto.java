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
		 * Electricity: grid = Σ giờ max(load - solar, 0) (điện mua lưới,
		 * GridSolarSplit); vndCost/usdCost = tiền grid theo giá grid.
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
		 * load = Σ sub-meter (tổng tiêu thụ = grid + solar, solar không cộng thêm).
		 */
		BigDecimal totalEnergyValue,

		/**
		 * load của kỳ trước.
		 */
		BigDecimal prevTotalEnergyValue,

		/**
		 * solarValue / totalEnergyValue (load) * 100.
		 */
		BigDecimal solarSharePercent,

		OffsetDateTime serverTime,

		/**
		 * Electricity: true khi kỳ hiện tại có giờ không có version giá
		 * (vndCost/usdCost chỉ gồm các giờ có giá) hoặc không có tariff
		 * (vndCost/usdCost = null). Water/Air: null.
		 */
		Boolean priceMissing,

		/**
		 * Như priceMissing, cho kỳ trước.
		 */
		Boolean prevPriceMissing

) {
}