package com.example.factory_utility_visualization_be.dto.overview.command;


import java.math.BigDecimal;

public record UtilityOverviewKpiDto(
		BigDecimal todayEnergy,
		String energyUnit,
		BigDecimal energyDiffPercent,

		BigDecimal todayCost,
		String currency,
		BigDecimal costDiffPercent,

		Integer activeAlarm,
		Integer criticalAlarm,

		String peakFactory,
		BigDecimal peakFactoryValue,
		BigDecimal peakFactorySharePercent,

		BigDecimal dataHealthPercent,
		Integer noDataCount,
		Integer staleCount
) {}