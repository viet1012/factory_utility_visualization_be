package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;

public interface SolarCostProjection {

	BigDecimal getSolarEnergyKwh();

	BigDecimal getNormalCostVnd();

	BigDecimal getSolarCostVnd();

	BigDecimal getSavingVnd();
}
