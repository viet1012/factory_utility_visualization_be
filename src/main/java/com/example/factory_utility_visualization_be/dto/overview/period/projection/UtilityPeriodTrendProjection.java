package com.example.factory_utility_visualization_be.dto.overview.period.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface UtilityPeriodTrendProjection {

	LocalDate getRecordDate();

	BigDecimal getValue();

	String getUnit();

}