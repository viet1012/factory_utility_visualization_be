package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface SolarDailyTrendProjection {

	LocalDate getRecordDate();

	BigDecimal getSolarKwh();

	BigDecimal getGridKwh();

	BigDecimal getTotalKwh();

	BigDecimal getSolarSharePercent();
}