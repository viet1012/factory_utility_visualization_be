package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;

public record SolarHourlyPointDto(

		int hour,

		BigDecimal energyKwh

) {
}