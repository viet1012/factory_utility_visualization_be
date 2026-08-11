package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;

public record SolarDetailSummaryDto(

		BigDecimal currentPowerKw,

		BigDecimal solarKwh,

		BigDecimal gridKwh,

		BigDecimal totalKwh,

		BigDecimal solarSharePercent

) {
}