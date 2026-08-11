package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;
import java.time.LocalDate;

public record SolarDailyTrendDto(

		LocalDate date,

		BigDecimal solarKwh,

		BigDecimal gridKwh,

		BigDecimal totalKwh,

		BigDecimal solarSharePercent

) {
}