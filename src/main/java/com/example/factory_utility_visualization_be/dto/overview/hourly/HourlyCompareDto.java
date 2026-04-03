package com.example.factory_utility_visualization_be.dto.overview.hourly;

import java.math.BigDecimal;

//public record HourlyCompareDto(
//		int scaleHour,           // 1..24
//		BigDecimal today,
//		BigDecimal yesterday
//) {}
public record HourlyCompareDto(
		int scaleHour,           // 0..23 hoặc 1..24 tùy bạn
		BigDecimal yesterday,
		BigDecimal today,
		BigDecimal yesterdayUsd,
		BigDecimal todayUsd
) {}