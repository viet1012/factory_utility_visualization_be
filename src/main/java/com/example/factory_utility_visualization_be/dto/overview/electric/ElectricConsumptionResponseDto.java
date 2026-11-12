package com.example.factory_utility_visualization_be.dto.overview.electric;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/utility/electric-consumption: data for the "Elec. Daily
 * Consumption" charts of F2 electric consumption.xlsx (FY26-Sep + Solar1).
 *
 * Per scope (KVH = A&B + C, FAC_AB, FAC_C) two chart blocks, each in MWh and K$:
 * - monthly: the previous bill periods, values = average PER DAY of the period
 * - daily: every day of the selected bill period (null after dataUntil)
 * Stacked bars = normal / highPeak / lowPeak (+ solar), line = total
 * (totalElectricity + solar) or totalElectricity.
 *
 * Bands are null (and every K$ value is null) when the tariff schedule of
 * F2_Utility_Cost_Master cannot be mapped to 3 bands; totals are still set.
 */
public record ElectricConsumptionResponseDto(
		String month,
		BillPeriod billPeriod,
		// last complete hour included (ISO-8601 +07:00), null = period not started
		String dataUntil,
		boolean tariffAvailable,
		UnitPrices unitPrices,
		List<Scope> scopes
) {

	public record BillPeriod(
			// e.g. "Sep (Aug21 ~ Sep20]" like the sheet
			String label,
			// [from, to) dates, to = first day of the next period
			String from,
			String to,
			int days
	) {}

	// $/kWh, null when tariff unavailable
	public record UnitPrices(
			BigDecimal gridNormal,
			BigDecimal gridHighPeak,
			BigDecimal gridLowPeak,
			BigDecimal solarNormal,
			BigDecimal solarHighPeak,
			BigDecimal solarLowPeak
	) {}

	public record Scope(
			// KVH | FAC_AB | FAC_C
			String code,
			String name,
			List<MonthPoint> monthly,
			List<DayPoint> daily,
			// sum of the daily rows (row "Total" of the sheet)
			Values dailyTotalMwh,
			Values dailyTotalKusd,
			// dailyTotal / elapsed days
			Values dailyAverageMwh,
			Values dailyAverageKusd,
			int elapsedDays
	) {}

	public record MonthPoint(
			// bill month yyyy-MM
			String month,
			String label,
			String from,
			String to,
			int days,
			Values perDayMwh,
			Values perDayKusd
	) {}

	public record DayPoint(
			String date,
			// Mon..Sun
			String weekday,
			// null when the day is after dataUntil
			Values mwh,
			Values kusd
	) {}

	public record Values(
			BigDecimal normal,
			BigDecimal highPeak,
			BigDecimal lowPeak,
			BigDecimal solar,
			BigDecimal totalElectricity,
			// totalElectricity + solar
			BigDecimal total
	) {}
}
