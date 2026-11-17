package com.example.factory_utility_visualization_be.dto.overview.electric;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/utility/electric-consumption: data for the "Elec. Daily
 * Consumption" charts of F2 electric consumption.xlsx (FY26-Sep + Solar1).
 *
 * Per scope (KVH = A&B + C, FAC_AB, FAC_C) two chart blocks, each in MWh and K$:
 * - monthly: the previous bill periods, values = average PER COMPLETE DAY
 * - daily: every day of the selected bill period
 * Stacked bars = normal / highPeak / lowPeak (grid bought from EVN, per
 * band) + solar, line = total (= load = consumption) or totalElectricity
 * (= grid). The sub-meters measure the load; grid = max(load - solar, 0) per
 * scope and hour, before the band split.
 *
 * null rules:
 * - a Values object is null when there is no data to show (no data at all /
 *   day not started / no complete day for an average)
 * - Values.solar is null when no SOLAR meter reported in that day / period
 *   (grid = load then)
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
			boolean priceMissing,
			// rough grid MWh not in dailyTotalMwh because of MAJOR meter issues:
			// sum over MAJOR meters of (their average kWh per valid hour in the
			// period x missingHours) / 1000; null = no MAJOR meter with valid
			// hours to estimate from
			BigDecimal estimatedMissingMwh,
			// sum / average of DayPoint.official; an EVN day always counts (even
			// partial / without measured data):
			// total = every EVN day + MEASURED days with data,
			// average = every EVN day + complete MEASURED days (divisor = their count)
			Values officialTotalMwh,
			Values officialTotalKusd,
			Values officialAverageMwh,
			Values officialAverageKusd,
			// days of officialTotal* from EVN / from the measured values
			int evnDays,
			int measuredDays
	) {}

	public record MeterIssue(
			// box_device_id
			String meter,
			String fac,
			// expected hours of the period up to dataUntil - hours with valid data
			int missingHours,
			// end of the last hour with valid data (ISO-8601 +07:00), null = never
			String lastDataAt,
			// MAJOR = some day of the period with no data at all (NO_DATA) or
			// missingHours >= 24; MINOR = only a few hours missing / dropped
			String severity,
			// MAJOR only: avg kWh per valid hour of this meter x missingHours, in MWh;
			// null for MINOR or a meter without any valid hour. Sum over meters =
			// Scope.estimatedMissingMwh (up to rounding)
			BigDecimal estimatedMissingMwh,
			// estimatedMissingMwh x the scope's average measured grid price of the
			// period (all bands), in K$; null when estimatedMissingMwh is null or
			// no day of the period is priced
			BigDecimal estimatedMissingKusd
	) {}

	public record MissingMeter(
			// box_device_id
			String meter,
			String fac,
			// hours WITH valid data on that day (not the missing hours)
			int validHours,
			// NO_DATA = no row at all, OUTLIER = some hours dropped as outliers,
			// PARTIAL = some hours without data
			String reason,
			// end of the last hour with valid data (ISO-8601 +07:00), null = never
			String lastDataAt
	) {
		// old JSON name of validHours, still written for the FE; remove once
		// the FE reads validHours
		@Deprecated
		@JsonProperty("hours")
		public int hours() {
			return validHours;
		}
	}

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
			boolean priceMissing,
			// average per day of the official values over every EVN day +
			// complete MEASURED days; null = no such day
			OfficialValues official,
			// days of official from EVN / from the measured values
			int evnDays,
			int measuredDays
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
			List<MissingMeter> missingMeters,
			// ESTIMATE: measured grid + loss calibration, null = no calibration
			// covers the day or no grid data (see Calibrated)
			Calibrated calibrated,
			// official EVN main meter (F2_Utility_Evn_Daily), null = not imported
			Evn evn,
			// EVN when imported, else = mwh / kusd; null = neither
			Official official
	) {}

	/**
	 * Official value of one scope x day (never the calibrated estimate):
	 * - source EVN (F2_Utility_Evn_Daily row of that day and scope; KVH from
	 *   its own KVH row, not FAC_AB + FAC_C): bands / totalElectricity = EVN
	 *   kWh, K$ = band kWh x grid price of that band on that day (same tariff
	 *   as the grid); solar (MWh, K$) = measured, null when the day has no
	 *   measured solar; total = load = totalElectricity + solar (no solar ->
	 *   = totalElectricity)
	 * - source MEASURED: copy of mwh / kusd
	 * Bands / K$ null when the day has no tariff version (same rule as Values).
	 */
	public record Official(
			Values mwh,
			Values kusd,
			// EVN | MEASURED
			String source
	) {}

	// monthly[]: average per complete day of the official values
	public record OfficialValues(
			Values mwh,
			Values kusd
	) {}

	/**
	 * Measured grid of the day + loss_kwh_per_hour (F2_Utility_Loss_Calibration)
	 * for EVERY HOUR WITH GRID DATA, split into bands per hour like the
	 * measured grid. Hours without data (partial day / missing meters) get
	 * nothing: a partial day stays partial (no fill of the missing meters).
	 * KVH is not calibrated on its own: per hour, calibrated KVH = calibrated
	 * FAC_AB + calibrated FAC_C; null unless BOTH facs have a calibration on
	 * that day. (Measured KVH grid = max(load - solar, 0) over all facs, so it
	 * can differ a little from FAC_AB grid + FAC_C grid.)
	 * MWh; bands null when not priced (same rule as Values).
	 */
	public record Calibrated(
			BigDecimal normal,
			BigDecimal highPeak,
			BigDecimal lowPeak,
			BigDecimal totalElectricity,
			// kWh per hour added (F2_Utility_Loss_Calibration.loss_kwh_per_hour);
			// KVH = FAC_AB loss + FAC_C loss (an hour where only one fac has
			// data gets only that fac's loss)
			BigDecimal calibrationLossPerHour,
			// hours the loss was added to (= hours with grid data)
			int calibratedHours,
			// F2_Utility_Loss_Calibration.id used: [id] for FAC_AB / FAC_C,
			// [FAC_AB id, FAC_C id] for KVH
			List<Long> calibrationIds,
			// always true: not a measured value
			@JsonProperty("isEstimated")
			boolean isEstimated
	) {}

	/** F2_Utility_Evn_Daily row of the day, MWh (from the sheet). */
	public record Evn(
			BigDecimal normal,
			BigDecimal highPeak,
			BigDecimal lowPeak,
			BigDecimal totalElectricity,
			// "Total Solar" row of the sheet, KVH only, else null
			BigDecimal solar,
			String sourceFile,
			// ISO-8601 +07:00
			String importedAt
	) {}

	/**
	 * Per hour and scope: load = sum of the grid sub-meters (consumption),
	 * grid = max(load - solar, 0) = bought from EVN.
	 */
	public record Values(
			// grid per tariff band (normal + highPeak + lowPeak = totalElectricity)
			BigDecimal normal,
			BigDecimal highPeak,
			BigDecimal lowPeak,
			// null = no SOLAR meter data
			BigDecimal solar,
			// grid = bought from EVN (compare with the EVN bill / sheet)
			BigDecimal totalElectricity,
			// consumption = sub-meter sum (K$: grid money + solar money)
			BigDecimal load,
			// = load (grid + solar, solar is not added on top of load)
			BigDecimal total
	) {}
}
