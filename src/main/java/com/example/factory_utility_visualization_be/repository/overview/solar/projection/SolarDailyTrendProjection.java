package com.example.factory_utility_visualization_be.repository.overview.solar.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface SolarDailyTrendProjection {

	LocalDate getRecordDate();

	BigDecimal getSolarKwh();

	BigDecimal getGridKwh();

	BigDecimal getTotalKwh();

	BigDecimal getSolarSharePercent();
}