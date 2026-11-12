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
		// kWh of every hour with data; grid of hours without a tariff version
		// in band UNPRICED (rate / usd null), see priceMissing
		List<Supply> supplyMix,
		List<DayTotal> last7Total,
		// true = some hour of the period with kWh has no tariff version: its kWh
		// is in supplyMix (grid as band UNPRICED, solar in SOLAR) but its money
		// is not in total.usd / supplyMix.usd (priced hours only)
		boolean priceMissing
) {

	// usd = priced hours only, null when no hour is priced; blendedRate =
	// usd / kWh of the priced hours
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
