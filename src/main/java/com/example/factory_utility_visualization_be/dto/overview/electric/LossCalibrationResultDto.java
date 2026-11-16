package com.example.factory_utility_visualization_be.dto.overview.electric;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /api/utility/loss-calibration/recompute result: the stored row and
 * every day it was computed from (transparency).
 *
 * loss_kwh_per_hour = median over sample days of (evn_total - measured_grid) / 24
 * sample day = EVN row exists AND every configured grid meter of the scope
 * has 24 valid (non-outlier) hours that day (coverage 100 %).
 */
public record LossCalibrationResultDto(
		long id,
		String scope,
		BigDecimal lossKwhPerHour,
		// = lossKwhPerHour x 24
		BigDecimal lossKwhPerDay,
		String effectiveFrom,
		// null = open ended
		String effectiveTo,
		String method,
		String createdAt,
		String createdBy,
		// window looked at: [windowFrom, windowTo)
		String windowFrom,
		String windowTo,
		int hourOffset,
		int sampleDays,
		// window days with an EVN row but not used (coverage < 100 % / no data)
		int evnDaysNotUsed,
		BigDecimal minDiffKwhPerHour,
		BigDecimal maxDiffKwhPerHour,
		// measured load (sub-meter sum) per hour over the sample days
		BigDecimal avgLoadKwhPerHour,
		// |lossKwhPerHour| / avgLoadKwhPerHour in %, null = no load
		BigDecimal lossPctOfAvgLoad,
		// stored anyway; e.g. loss < 0 (measured > EVN, likely a meter
		// counted twice) or |loss| > 30 % of the average hourly load
		List<String> warnings,
		List<Sample> samples,
		List<Excluded> excluded
) {

	public record Sample(
			String day,
			BigDecimal evnTotalKwh,
			BigDecimal measuredGridKwh,
			BigDecimal measuredLoadKwh,
			// (evn - measured) / 24
			BigDecimal diffKwhPerHour
	) {}

	public record Excluded(
			String day,
			// NO_MEASURED_DATA | COVERAGE_BELOW_100
			String reason,
			// meter-hours with valid data / expected (meters x 24), %
			BigDecimal coveragePct
	) {}
}
