package com.example.factory_utility_visualization_be.dto.overview.solar;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SolarDashboardDto(

		String facId,

		LocalDateTime generatedAt,

		BigDecimal currentPowerKw,

		BigDecimal solarKwh,

		BigDecimal gridKwh,

		BigDecimal totalKwh,

		BigDecimal solarSharePercent,

		BigDecimal todayCo2Kg,

		BigDecimal todayCo2Ton,

		BigDecimal todayEquivalentTrees

) {
}