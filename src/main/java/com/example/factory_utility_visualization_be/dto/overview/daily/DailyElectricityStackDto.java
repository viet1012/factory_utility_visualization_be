package com.example.factory_utility_visualization_be.dto.overview.daily;


import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyElectricityStackDto(

		LocalDate date,

		BigDecimal gridKwh,

		BigDecimal solarKwh,

		BigDecimal totalKwh

) {
}