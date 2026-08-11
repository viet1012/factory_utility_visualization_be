package com.example.factory_utility_visualization_be.dto.overview.solar.detail;

import java.math.BigDecimal;

public interface SolarHourlyProfileProjection {

	Integer getScaleHour();

	BigDecimal getEnergyKwh();
}