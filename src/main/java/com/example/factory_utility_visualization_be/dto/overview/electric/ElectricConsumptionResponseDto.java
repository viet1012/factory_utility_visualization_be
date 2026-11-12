package com.example.factory_utility_visualization_be.dto.overview.electric;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/utility/electric-consumption: data for the "Elec. Daily
 * Consumption" charts of F2 electric consumption.xlsx (FY26-Sep + Solar1).
 *
 * Per scope (KVH = A&B + C, FAC_AB, FAC_C) two chart blocks, each in MWh and K$:
 * - monthly: the previous bill periods, values = average PER COMPLETE DAY
 * - daily: every day of the selected bill period
 * Stacked bars = normal / highPeak / lowPeak (+ solar), line = total
 * (totalElectricity + solar) or totalElectricity.
 *
 * null rules:
 * - a Values object is null when there is no data to show (no data at all /
 *   day not started / no complete day for an average)
 * - Values.solar is null when no SOLAR meter reported in that day / period
 *   (total is then totalElectricity only)
 * - bands and every K$ value are null when the tariff schedule of
 *   F2_Utility_Cost_Master cannot be mapped to 3 bands
 *
 * A day is complete when it is over and its meter-hour coverage (meter-hours
 * with grid data / expected meters x 24) is >= minCoveragePct, expected
 * meters = most grid meters seen in one hour within +/- meterWindowDays; so a
 * day where some meters stopped reporting is partial. Partial days are
 * returned with partial = true but are left out of every average.
 *
 * Meter-hours far above the meter's usual kWh/hour (cumulative register or
 * catch-up values after a gap) are dropped and treated as missing; the
 * dropped energy is reported as excludedMwh.
 */
public record ElectricConsumptionResponseDto(
		String month,
		BillPeriod billPeriod,
		// exclusive end of the data (last complete hour, ISO-8601 +07:00), null = period not started
		String dataUntil,
		boolean tariffAvailable,
		BigDecimal minCoveragePct,
		int hourOffset,
		// data before this day is not used (values null), null = all data
		String historyStartDate,
		// box_device_id not counted (duplicate meters), upper case
		List<String> excludedMeters,
		UnitPrices unitPrices,
		List<Scope> scopes
) {

	public record BillPeriod(
			// e.g. "Sep (Aug21 ~ Sep20]" like cell C5 of the sheet
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
			// most grid meters seen in one hour of the selected period
			int meters,
			// days of the selected period already started
			int elapsedDays,
			// days with any data / complete days
			int daysWithData,
			int completeDays,
			// sum of every day with data (row "Total" of the sheet), null = no data
			Values dailyTotalMwh,
			Values dailyTotalKusd,
			// average of the complete days, null = no complete day
			Values dailyAverageMwh,
			Values dailyAverageKusd
	) {}

	public record MonthPoint(
			// bill month yyyy-MM = month the period ends in
			String month,
			String label,
			String from,
			String to,
			int days,
			// most grid meters seen in one hour of the period
			int meters,
			int daysWithData,
			int completeDays,
			// completeDays < days
			boolean partial,
			// MWh dropped as outliers (cumulative / catch-up values), null = none
			BigDecimal excludedMwh,
			// average per complete day, null = no complete day
			Values perDayMwh,
			Values perDayKusd
	) {}

	public record DayPoint(
			String date,
			// Mon..Sun
			String weekday,
			// hours with grid data (0..24)
			int hoursWithData,
			// expected grid meters (most seen in one hour within +/- meterWindowDays)
			int meters,
			// meter-hours with grid data / (meters x 24) in %, null = no data
			BigDecimal coveragePct,
			// has data but is still running or coveragePct < minCoveragePct
			boolean partial,
			// MWh dropped as outliers (cumulative / catch-up values), null = none
			BigDecimal excludedMwh,
			// null when the day has no data or is after dataUntil
			Values mwh,
			Values kusd
	) {}

	public record Values(
			BigDecimal normal,
			BigDecimal highPeak,
			BigDecimal lowPeak,
			// null = no SOLAR meter data
			BigDecimal solar,
			BigDecimal totalElectricity,
			// totalElectricity + solar
			BigDecimal total
	) {}
}
