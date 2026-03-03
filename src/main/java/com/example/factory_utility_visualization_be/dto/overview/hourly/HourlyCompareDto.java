package com.example.factory_utility_visualization_be.dto.overview.hourly;

import java.math.BigDecimal;

public record HourlyCompareDto(
		int scaleHour,           // 1..24
		BigDecimal today,
		BigDecimal yesterday
) {}