package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MonthlySummaryProjection {

	String getName();

	String getCate();

	String getMonth();

	BigDecimal getValue();

	String getUnit();

	LocalDateTime getTimestamp();
}