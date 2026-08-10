package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MonthlySummaryProjection {

	String getName();

	String getCate();

	String getUnit();

	String getMonth();

	BigDecimal getMinValue();

	BigDecimal getMaxValue();

	BigDecimal getPrevMinValue();

	BigDecimal getPrevMaxValue();

	/**
	 * Điện lưới/điện tiêu thụ, đã loại các thiết bị SOLAR.
	 */
	BigDecimal getValue();

	BigDecimal getAvgValue();

	BigDecimal getVndCost();

	BigDecimal getUsdCost();

	BigDecimal getPrevValue();

	BigDecimal getPrevAvgValue();

	BigDecimal getPrevVndCost();

	BigDecimal getPrevUsdCost();

	BigDecimal getDeltaValue();

	BigDecimal getDeltaPercent();

	LocalDateTime getPickAt();

	// =========================================================
	// SOLAR
	// Chỉ có giá trị đối với dòng Electricity.
	// Các dòng Water/Air có thể trả null.
	// =========================================================

	BigDecimal getSolarValue();

	BigDecimal getPrevSolarValue();

	BigDecimal getTotalEnergyValue();

	BigDecimal getPrevTotalEnergyValue();

	BigDecimal getSolarSharePercent();
}