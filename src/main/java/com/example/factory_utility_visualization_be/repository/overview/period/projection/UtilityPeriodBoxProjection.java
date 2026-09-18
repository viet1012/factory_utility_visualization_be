package com.example.factory_utility_visualization_be.repository.overview.period.projection;


import java.math.BigDecimal;

public interface UtilityPeriodBoxProjection {

	String getBoxDeviceId();

	String getBoxId();

	BigDecimal getValue();
}