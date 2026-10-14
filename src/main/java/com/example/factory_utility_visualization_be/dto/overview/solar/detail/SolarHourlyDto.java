package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SolarHourlyDto(

		LocalDate date,

		String facId,

		List<SolarHourlyFacilityDto> facilities,

		List<SolarHourKwhDto> totalHours,

		BigDecimal totalKwh,

		BigDecimal peakKwh,

		Integer peakHour

) {
}
