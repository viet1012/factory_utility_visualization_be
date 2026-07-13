package com.example.factory_utility_visualization_be.dto.overview.hourly;


import java.math.BigDecimal;

public interface HourlySensorCompareProjection {

	String getUtilityType();

	Integer getScaleHour();

	BigDecimal getYesterday();

	BigDecimal getToday();
}