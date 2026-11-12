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
 * - bands and every K$ value are null when no tariff is usable, and per day
 *   when that day has no tariff version (Scope / MonthPoint.priceMissing)
 *
 * A day is complete when it is over and its meter-hour coverage (meter-hours
 * with grid data / expected meter-hours) is >= minCoveragePct; so a day where
 * some meters stopped reporting is partial. Partial days are returned with
 * partial = true but are left out of every average. Expected meter-hours:
 * - selected period (daily, Scope.meters): configured grid meters of the
 *   scope x hours of the day up to dataUntil
 * - monthly history: most grid meters seen in one hour within
 *   +/- meterWindowDays x 24
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

	// $/kWh of the tariff version of the last hour with data (dataUntil - 1h),
	// null when tariff unavailable or no version covers that day
	public record UnitPrices(
			BigDecimal gridNormal,
			BigDecimal gridHighPeak,
			BigDecimal gridLowPeak,
			BigDecimal solarNormal,
			BigDecimal solarHighPeak,
			BigDecimal solarLowPeak,
			// effective_from (yyyy-MM-dd) of that version, null = no lower bound
			String effectiveFrom
	) {}

	public record Scope(
			// KVH | FAC_AB | FAC_C
			String code,
			String name,
			List<MonthPoint> monthly,
			List<DayPoint> daily,
			// configured grid meters of the scope
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
			Values dailyAverageKusd,
			// meters with missing hours in the selected period (up to dataUntil),
			// most missing hours first
			List<MeterIssue> meterIssues,
			// some day with data has no tariff version: its bands / K$ are null
			// in daily[] and left out of dailyTotal / dailyAverage bands and K$
			boolean priceMissing
	) {}

	public record MeterIssue(
			// box_device_id
			String meter,
			String fac,
			// expected hours of the period up to dataUntil - hours with valid data
			int missingHours,
			// end of the last hour with valid data (ISO-8601 +07:00), null = never
			String lastDataAt
	) {}

	public record MissingMeter(
			// box_device_id
			String meter,
			String fac,
			// hours with valid data on that day
			int hours,
			// NO_DATA = no row at all, OUTLIER = some hours dropped as outliers,
			// PARTIAL = some hours without data
			String reason,
			// end of the last hour with valid data (ISO-8601 +07:00), null = never
			String lastDataAt
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
			Values perDayKusd,
			// some complete day has no tariff version (its bands / K$ are not
			// in the averages)
			boolean priceMissing
	) {}

	public record DayPoint(
			String date,
			// Mon..Sun
			String weekday,
			// hours with grid data (0..24)
			int hoursWithData,
			// configured grid meters of the scope
			int meters,
			// meter-hours with grid data / (meters x hours of the day up to
			// dataUntil) in %, null = no data
			BigDecimal coveragePct,
			// has data but is still running or coveragePct < minCoveragePct
			boolean partial,
			// MWh dropped as outliers (cumulative / catch-up values), null = none
			BigDecimal excludedMwh,
			// null when the day has no data or is after dataUntil
			Values mwh,
			Values kusd,
			// configured meters with at least one valid hour on that day
			int reportingMeters,
			// configured meters with fewer valid hours than expected for the day
			// (NO_DATA first, then fewest hours, then name); [] after dataUntil
			List<MissingMeter> missingMeters
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
