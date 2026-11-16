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
		List<String> warnings
) {

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
