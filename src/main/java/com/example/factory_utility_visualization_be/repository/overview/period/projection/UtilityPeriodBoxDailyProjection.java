package com.example.factory_utility_visualization_be.repository.overview.period.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface UtilityPeriodBoxDailyProjection {

	String getBoxDeviceId();

	String getBoxId();

	LocalDate getRecordDate();

	BigDecimal getValue();
}