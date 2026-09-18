package com.example.factory_utility_visualization_be.repository.overview.solar.projection;


import java.math.BigDecimal;



public interface SolarDashboardProjection {

	BigDecimal getCurrentPowerKw();

	BigDecimal getSolarKwh();

	BigDecimal getGridKwh();

	BigDecimal getTotalKwh();

	BigDecimal getSolarSharePercent();
}