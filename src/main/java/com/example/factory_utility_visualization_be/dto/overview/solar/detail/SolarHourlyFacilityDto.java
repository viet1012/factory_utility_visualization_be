package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;
import java.util.List;

public record SolarHourlyFacilityDto(

		String facId,

		BigDecimal totalKwh,

		BigDecimal peakKwh,

		Integer peakHour,

		List<SolarHourKwhDto> hours

) {
}
