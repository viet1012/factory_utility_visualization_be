package com.example.factory_utility_visualization_be.dto.overview.daily;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface UtilityDailyElectricityStackProjection {

	LocalDate getRecordDate();

	BigDecimal getGridKwh();

	BigDecimal getSolarKwh();

	BigDecimal getTotalKwh();
}