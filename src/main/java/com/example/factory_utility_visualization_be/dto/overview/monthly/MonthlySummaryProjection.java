package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;

public interface MonthlySummaryProjection {

	String getName();

	String getCate();   // 👈 thêm cái này

	String getMonth();

	BigDecimal getValue();

	String getUnit();
}