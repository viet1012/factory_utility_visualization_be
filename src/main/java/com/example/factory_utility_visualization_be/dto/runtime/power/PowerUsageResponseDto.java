package com.example.factory_utility_visualization_be.dto.runtime.power;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/utility/power-usage. Timestamps are ISO-8601 with the plant
 * offset (+07:00), same format as /series/history. kWh = grid kWh (non-SOLAR
 * meters) + solar kWh; USD and rates are USD/kWh. changePct is null when the
 * previous value is 0.
 */
public record PowerUsageResponseDto(
		String period,
		String from,
		String to,
		String prevFrom,
		String prevTo,
		Total total,
		// period=day|month only
		@JsonInclude(JsonInclude.Include.NON_NULL) Forecast forecast,
		List<Factory> byFactory,
		// compressed air cabinets (grid kWh)
		List<Purpose> byPurpose,
		boolean purposeConfigured,
		// empty when the tariff schedule / exchange rate is not usable
		List<Supply> supplyMix,
		List<DayTotal> last7Total
) {

	// usd / blendedRate are null when supplyMix is empty
	public record Total(
			BigDecimal kwh,
			BigDecimal prevKwh,
			BigDecimal changePct,
			BigDecimal usd,
			BigDecimal blendedRate
	) {}

	public record Forecast(
			BigDecimal monthToDateKwh,
			BigDecimal projectedKwh,
			int dayOfMonth,
			int daysInMonth,
			BigDecimal changePctVsPrevMonth
	) {}

	public record Factory(
			String facId,
			BigDecimal kwh,
			BigDecimal prevKwh,
			BigDecimal sharePct,
			BigDecimal changePct
	) {}

	public record Purpose(
			String code,
			String name,
			String description,
			BigDecimal kwh,
			BigDecimal prevKwh,
			BigDecimal changePct,
			List<BigDecimal> last7
	) {}

	// band: HIGH_PEAK | NORMAL | LOW_PEAK | SOLAR
	public record Supply(
			String band,
			BigDecimal kwh,
			BigDecimal sharePct,
			BigDecimal rate,
			BigDecimal usd
	) {}

	// date: yyyy-MM-dd
	public record DayTotal(
			String date,
			BigDecimal kwh
	) {}
}
