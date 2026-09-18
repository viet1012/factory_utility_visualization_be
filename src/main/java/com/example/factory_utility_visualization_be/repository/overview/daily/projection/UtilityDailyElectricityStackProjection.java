package com.example.factory_utility_visualization_be.repository.overview.daily.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface UtilityDailyElectricityStackProjection {

	LocalDate getRecordDate();

	BigDecimal getGridKwh();

	BigDecimal getSolarKwh();

	BigDecimal getTotalKwh();
}