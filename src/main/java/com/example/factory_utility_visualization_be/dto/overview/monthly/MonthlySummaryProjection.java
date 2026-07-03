package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MonthlySummaryProjection {
	String getName();
	String getCate();
	String getUnit();
	String getMonth();

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
}