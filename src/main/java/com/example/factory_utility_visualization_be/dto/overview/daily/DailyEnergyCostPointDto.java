package com.example.factory_utility_visualization_be.dto.overview.daily;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyEnergyCostPointDto(
		LocalDate recordDate,
		BigDecimal energyKwh,
		BigDecimal costUsd
) {
}