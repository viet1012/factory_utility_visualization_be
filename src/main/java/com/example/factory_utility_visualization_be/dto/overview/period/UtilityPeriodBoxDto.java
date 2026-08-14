package com.example.factory_utility_visualization_be.dto.overview.period;


import java.math.BigDecimal;

public record UtilityPeriodBoxDto(

		String boxDeviceId,

		String boxId,

		BigDecimal total,

		BigDecimal sharePercent

) {
}