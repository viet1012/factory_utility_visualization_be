package com.example.factory_utility_visualization_be.dto.overview.daily;

public interface UtilityDailyDashboardProjection {

	String getUtilityType();

	java.sql.Date getRecordDate();

	java.math.BigDecimal getValue();
}
