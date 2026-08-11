package com.example.factory_utility_visualization_be.dto.overview.solar.detail;

import java.math.BigDecimal;

public record SolarEnvironmentalDto(

		BigDecimal co2Kg,

		BigDecimal co2Ton,

		BigDecimal equivalentTrees,

		BigDecimal co2Factor

) {
}