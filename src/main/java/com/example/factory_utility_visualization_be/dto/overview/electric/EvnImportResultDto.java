package com.example.factory_utility_visualization_be.dto.overview.electric;

import java.util.List;

/**
 * POST /api/utility/evn-daily/import result. Row = one (day, scope).
 * Rows with errors are NOT saved; the other rows of the file are.
 */
public record EvnImportResultDto(
		String sourceFile,
		// true = nothing written (dryRun=true)
		boolean dryRun,
		List<SheetInfo> sheets,
		// new (day, scope)
		int inserted,
		// existing (day, scope) with different kWh
		int updated,
		// existing (day, scope) with the same kWh (not written)
		int unchanged,
		// total empty / 0 (day not entered yet in the sheet)
		int skipped,
		// rows rejected by a check (see errors)
		int rejected,
		List<Error> errors,
		List<String> warnings,
		// parameters for POST /loss-calibration/recompute after this import
		CalibrationWindow calibration
) {

	/**
	 * Window [until - days, until) for POST /loss-calibration/recompute
	 * (scope=FAC_AB then scope=FAC_C): days with EVN rows (DB + this file
	 * when dryRun) and measured data (history-start-date).
	 */
	public record CalibrationWindow(
			// false = no EVN day inside the measured history: recompute would answer 400
			boolean available,
			// yyyy-MM-dd, last day with EVN total > 0 of BOTH FAC_AB and FAC_C, null = none
			String lastEvnDay,
			// yyyy-MM-dd exclusive = lastEvnDay + 1 (not after today), null when not available
			String until,
			// 1..366, null when not available
			Integer days,
			// yyyy-MM-dd first day of the window, null when not available
			String from
	) {}

	public record SheetInfo(
			String sheet,
			// bill month yyyy-MM from the sheet name (or ?month)
			String billMonth,
			// [from, to)
			String from,
			String to,
			// days x scopes with total > 0
			int rows
	) {}

	public record Error(
			String sheet,
			// yyyy-MM-dd, null = whole block / sheet
			String day,
			// KVH | FAC_AB | FAC_C, null = whole sheet
			String scope,
			// e.g. "BH33"
			String cell,
			String message
	) {}
}
