package com.example.factory_utility_visualization_be.repository.overview.daily.projection;

public interface UtilityDailyDashboardProjection {

	String getUtilityType();

	java.sql.Date getRecordDate();

	java.math.BigDecimal getValue();
}
