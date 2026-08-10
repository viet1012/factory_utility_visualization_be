package com.example.factory_utility_visualization_be.dto.overview.solar;


import java.math.BigDecimal;



public interface SolarDashboardProjection {

	BigDecimal getCurrentPowerKw();

	BigDecimal getSolarKwh();

	BigDecimal getGridKwh();

	BigDecimal getTotalKwh();

	BigDecimal getSolarSharePercent();
}