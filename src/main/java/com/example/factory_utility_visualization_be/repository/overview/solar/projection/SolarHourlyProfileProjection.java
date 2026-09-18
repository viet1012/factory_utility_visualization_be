package com.example.factory_utility_visualization_be.repository.overview.solar.projection;

import java.math.BigDecimal;

public interface SolarHourlyProfileProjection {

	Integer getScaleHour();

	BigDecimal getEnergyKwh();
}