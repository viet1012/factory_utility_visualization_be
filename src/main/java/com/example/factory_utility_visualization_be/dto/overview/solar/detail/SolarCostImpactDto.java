package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;

public record SolarCostImpactDto(

		BigDecimal solarEnergyKwh,

		BigDecimal normalCostVnd,

		BigDecimal solarCostVnd,

		BigDecimal savingVnd,

		BigDecimal normalCostUsd,

		BigDecimal solarCostUsd,

		BigDecimal savingUsd,

		BigDecimal savingPercent

) {
}