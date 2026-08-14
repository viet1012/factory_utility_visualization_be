package com.example.factory_utility_visualization_be.dto.overview.period;

import java.math.BigDecimal;
import java.util.List;

public record UtilityPeriodBoxTrendDto(

		String boxId,

		List<BigDecimal> values,

		BigDecimal total

) {
}