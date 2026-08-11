package com.example.factory_utility_visualization_be.dto.overview.solar.detail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SolarHourlyProfileDto(

		LocalDate date,

		BigDecimal totalEnergyKwh,

		BigDecimal peakEnergyKwh,

		Integer peakHour,

		List<SolarHourlyPointDto> points

) {
}