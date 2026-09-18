package com.example.factory_utility_visualization_be.repository.overview.solar.projection;


import java.math.BigDecimal;

public interface SolarCostProjection {

	BigDecimal getSolarEnergyKwh();

	BigDecimal getNormalCostVnd();

	BigDecimal getSolarCostVnd();

	BigDecimal getSavingVnd();
}
