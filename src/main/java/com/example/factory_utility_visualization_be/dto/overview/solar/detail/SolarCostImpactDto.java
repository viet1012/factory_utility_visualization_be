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

		BigDecimal savingPercent,

		// true = some hour with kWh has no tariff version (money above covers
		// priced hours only); every money field is null when no tariff at all
		boolean priceMissing

) {
}