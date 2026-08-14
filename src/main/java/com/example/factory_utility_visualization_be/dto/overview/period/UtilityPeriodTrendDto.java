package com.example.factory_utility_visualization_be.dto.overview.period;


import java.math.BigDecimal;
import java.time.LocalDate;

public record UtilityPeriodTrendDto(

		LocalDate date,

		BigDecimal value

) {
}