package com.example.factory_utility_visualization_be.repository.overview.hourly.projection;


import java.math.BigDecimal;

public interface HourlySensorCompareProjection {

	String getUtilityType();

	Integer getScaleHour();

	BigDecimal getYesterday();

	BigDecimal getToday();
}