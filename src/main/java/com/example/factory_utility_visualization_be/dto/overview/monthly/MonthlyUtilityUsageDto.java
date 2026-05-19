package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MonthlyUtilityUsageDto(
		String fac,
		String boxId,
		String boxDeviceId,
		String name,
		String cate,
		String unit,
		BigDecimal usedValue,
		LocalDateTime firstPickAt,
		LocalDateTime lastPickAt,
		BigDecimal firstValue,
		BigDecimal lastValue
) {}