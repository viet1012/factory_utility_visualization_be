package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Cell;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Sheet;
import org.apache.poi.ss.util.CellReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Daily EVN block of one monthly sheet of F2 electric consumption.xlsx.
 * Two layouts exist, both found (not fixed) by this parser:
 *
 *   "FY22-Sep" .. "FY26-Apr"            "FY26-May + Solar1" ..
 *   row 23  block labels                 row 25
 *   row 26  weekday (Mon..Sun)           row 28
 *   row 27  day (first = date)           row 29
 *   row 28  Normal time                  row 30
 *   row 29  High-peak time               row 31
 *   row 30  Low-peak time                row 32
 *   row 31  Total                        row 33  Total Electricity
 *   -                                    row 34  Total Solar (KVH only)
 *
 * Block labels: "TTL KVH: A&B + C" | "Trans No.1 - Fac C" | "Trans No.2 - Fac A&B"
 * (the label row is the first row of FIRST_ROW..LAST_ROW holding all three).
 * The value rows are found by the row names under the KVH label (column B or C).
 *
 * The columns move every month, so the first day column of a block is the
 * first column after its label where the day row is a date, the weekday row
 * is a weekday and the next column is the next day number.
 *
 * Bill period: [first day, first day + 1 month). The first day is 21 since
 * Nov 2022, 26 before (FY22-Sep / Oct): it is read from the sheet, not fixed.
 * The YEAR of the date cells is often wrong and the sheet names mix fiscal
 * years (FY23-Jan = Jan 2024) and calendar years (FY25-Jan = Jan 2025), so
 * the year is the candidate whose weekday matches the weekday row.
 */
public final class EvnSheetParser {

	/** Rows the reader must load (both layouts). */
	public static final int FIRST_ROW = 18;
	public static final int LAST_ROW = 45;

	public static final String KVH = "KVH";
	public static final String FAC_AB = "FAC_AB";
	public static final String FAC_C = "FAC_C";

	private static final List<String> SCOPES = List.of(KVH, FAC_AB, FAC_C);

	// "FY26-Sep + Solar1", "FY26-May + Solar 1 ", "FY22-Jan ", "FY24-Jun"
	// (not "FY23-Aug (2)" copies nor "FY21-FY26 Sum")
	private static final Pattern SHEET_NAME = Pattern.compile(
			"^\\s*FY(\\d{2})-([A-Za-z]{3})[A-Za-z]*\\s*(?:\\+\\s*Solar\\s*1)?\\s*$", Pattern.CASE_INSENSITIVE);

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	// a period never has more days than this (guards a broken layout)
	private static final int MAX_DAYS = 31;

	private EvnSheetParser() {
	}

	/** One scope x day of the sheet, kWh. */
	public record ParsedRow(
			String sheet,
			LocalDate day,
			String scope,
			// column of the day, e.g. "BH"
			String column,
			// row of the Normal / Total value, for error messages
			int normalRow,
			int totalRow,
			BigDecimal normalKwh,
			BigDecimal highKwh,
			BigDecimal lowKwh,
			BigDecimal totalKwh,
			// KVH of the Solar layout only
			BigDecimal solarKwh
	) {
		// same day, scope and kWh (sheet / cell ignored)
		public boolean sameValues(ParsedRow other) {
			return day.equals(other.day) && scope.equals(other.scope)
					&& eq(normalKwh, other.normalKwh) && eq(highKwh, other.highKwh) && eq(lowKwh, other.lowKwh)
					&& eq(totalKwh, other.totalKwh) && eq(solarKwh, other.solarKwh);
		}

		private static boolean eq(BigDecimal a, BigDecimal b) {
			return a == null ? b == null : b != null && a.compareTo(b) == 0;
		}
	}

	public record Issue(String sheet, String day, String scope, String cell, String message) {}

	public record SheetResult(
			String sheet,
			// null when not resolved (see errors) or no block
			YearMonth billMonth,
			LocalDate from,
			// exclusive
			LocalDate to,
			// false = the sheet has no daily EVN block at all (FY22-Apr .. Aug)
			boolean hasBlock,
			// days with total > 0
			List<ParsedRow> rows,
			// scope x day with total empty / 0 (not entered yet)
			int skippedEmpty,
			List<Issue> errors,
			List<String> warnings
	) {}

	private record Layout(int labelRow, int weekdayRow, int dayRow, int normalRow, int highRow, int lowRow,
			int totalRow, Integer solarRow) {}

	public static boolean isEvnSheet(String sheetName) {
		return sheetName != null && SHEET_NAME.matcher(sheetName).matches();
	}

	/**
	 * Bill months the sheet name can mean: FY26-Sep -> 2026-09; Jan..Mar
	 * also year + 1 (fiscal year from April: FY23-Jan = 2024-01).
	 */
	public static List<YearMonth> billMonthCandidates(String sheetName) {
		if (sheetName == null) {
			return List.of();
		}

		Matcher m = SHEET_NAME.matcher(sheetName);

		if (!m.matches()) {
			return List.of();
		}

		Optional<Month> month = month(m.group(2));

		if (month.isEmpty()) {
			return List.of();
		}

		int year = 2000 + Integer.parseInt(m.group(1));
		YearMonth named = YearMonth.of(year, month.get());

		return month.get().getValue() <= 3 ? List.of(named, named.plusYears(1)) : List.of(named);
	}

	/**
	 * @param billMonths candidate bill months (from the sheet name or ?month=)
	 */
	public static SheetResult parse(Sheet sheet, List<YearMonth> billMonths) {
		List<ParsedRow> rows = new ArrayList<>();
		List<Issue> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		int[] skipped = {0};
		String name = sheet.name();

		// label row: first row with the three block labels
		Integer labelRow = null;
		Map<String, Integer> labelCol = Map.of();
		boolean anyLabel = false;

		for (int r = FIRST_ROW; r <= LAST_ROW && labelRow == null; r++) {
			Map<String, Integer> found = labels(sheet, r);
			anyLabel |= !found.isEmpty();

			if (found.keySet().containsAll(SCOPES)) {
				labelRow = r;
				labelCol = found;
			}
		}

		if (labelRow == null) {
			if (!anyLabel) {
				return new SheetResult(name, null, null, null, false, rows, 0, errors, warnings);
			}
			errors.add(new Issue(name, null, null, "rows " + FIRST_ROW + ".." + LAST_ROW,
					"no row with the 3 block labels (TTL KVH / Trans No.1 - Fac C / Trans No.2 - Fac A&B)"));
			return new SheetResult(name, null, null, null, true, rows, 0, errors, warnings);
		}

		Layout layout = layout(sheet, labelRow, labelCol.get(KVH));

		if (layout == null) {
			errors.add(new Issue(name, null, null, CellReference.convertNumToColString(labelCol.get(KVH)) + labelRow,
					"row names Normal time / High-peak time / Low-peak time / Total not found under the block label"));
			return new SheetResult(name, null, null, null, true, rows, 0, errors, warnings);
		}

		TreeMap<Integer, String> boundaries = boundaries(sheet, labelRow);
		YearMonth billMonth = null;
		LocalDate from = null;

		for (String scope : SCOPES) {
			int label = labelCol.get(scope);
			Integer next = boundaries.higherKey(label);
			int end = next == null ? Integer.MAX_VALUE : next;
			Integer start = findStart(sheet, layout, label, end);

			if (start == null) {
				errors.add(new Issue(name, null, scope,
						"row " + layout.dayRow() + " after " + CellReference.convertNumToColString(label),
						"first day column not found (a date in row " + layout.dayRow() + " with a weekday in row "
								+ layout.weekdayRow() + " above it)"));
				continue;
			}

			Cell first = sheet.cell(layout.dayRow(), start);
			LocalDate cellDate = first.dateValue().toLocalDate();
			String weekday = weekday(sheet.cell(layout.weekdayRow(), start));
			List<LocalDate> candidates = firstDayCandidates(cellDate, weekday, billMonths);

			if (candidates.size() != 1 && from != null && from.getMonthValue() == cellDate.getMonthValue()
					&& from.getDayOfMonth() == cellDate.getDayOfMonth()) {
				// weekday typo in this block only (FY23-Mar Fac C): the first block proved the year
				warnings.add(name + " " + scope + ": weekday '" + weekday + "' in " + ref(layout.weekdayRow(), start)
						+ " but " + from + " is " + from.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
						+ " (year taken from the first block)");
				candidates = List.of(from);
			}

			if (candidates.size() != 1) {
				errors.add(new Issue(name, null, scope, ref(layout.dayRow(), start),
						(candidates.isEmpty() ? "no year of bill month " + billMonths : "several years " + candidates)
								+ " gives " + weekday + " for day " + first.dateValue().toLocalDate().getDayOfMonth()
								+ "/" + first.dateValue().getMonthValue() + ": pass ?sheet= and ?month=yyyy-MM"));
				continue;
			}

			LocalDate blockFrom = candidates.get(0);

			if (from == null) {
				from = blockFrom;
				billMonth = billMonthOf(blockFrom, billMonths);
			} else if (!from.equals(blockFrom)) {
				errors.add(new Issue(name, blockFrom.toString(), scope, ref(layout.dayRow(), start),
						"block starts " + blockFrom + " but the first block starts " + from + ": block not imported"));
				continue;
			}

			parseBlock(sheet, layout, scope, start, blockFrom, blockFrom.plusMonths(1), rows, errors, warnings, skipped);
		}

		return new SheetResult(name, billMonth, from, from == null ? null : from.plusMonths(1), true, rows,
				skipped[0], errors, warnings);
	}

	// ---------------------------------------------------------------------

	private static Map<String, Integer> labels(Sheet sheet, int row) {
		Map<String, Integer> found = new HashMap<>();

		sheet.row(row).forEach((col, cell) -> {
			String upper = upper(cell);

			if (upper.contains("TTL KVH")) {
				found.merge(KVH, col, Math::min);
			} else if (upper.startsWith("TRANS NO") && upper.contains("FAC A&B")) {
				found.merge(FAC_AB, col, Math::min);
			} else if (upper.startsWith("TRANS NO") && upper.contains("FAC C")) {
				found.merge(FAC_C, col, Math::min);
			}
		});

		return found;
	}

	// block labels and boundaries (any block label, solar blocks included)
	private static TreeMap<Integer, String> boundaries(Sheet sheet, int labelRow) {
		TreeMap<Integer, String> boundaries = new TreeMap<>();

		sheet.row(labelRow).forEach((col, cell) -> {
			String upper = upper(cell);

			if (upper.contains("TTL KVH") || upper.startsWith("TRANS NO") || upper.startsWith("SOLAR LOT")) {
				boundaries.put(col, upper);
			}
		});

		return boundaries;
	}

	// Normal / High / Low / Total (/ Total Solar) on consecutive rows under the label
	private static Layout layout(Sheet sheet, int labelRow, int nameCol) {
		for (int r = labelRow + 1; r <= labelRow + 8; r++) {
			if (!rowName(sheet, r, nameCol).startsWith("NORMAL")) {
				continue;
			}

			String total = rowName(sheet, r + 3, nameCol);

			if (!rowName(sheet, r + 1, nameCol).startsWith("HIGH") || !rowName(sheet, r + 2, nameCol).startsWith("LOW")
					|| !total.startsWith("TOTAL") || total.startsWith("TOTAL SOLAR")) {
				return null;
			}

			Integer solar = rowName(sheet, r + 4, nameCol).startsWith("TOTAL SOLAR") ? r + 4 : null;

			return new Layout(labelRow, r - 2, r - 1, r, r + 1, r + 2, r + 3, solar);
		}

		return null;
	}

	// row name in the label column or the next one
	private static String rowName(Sheet sheet, int row, int col) {
		String a = upper(sheet.cell(row, col));
		return a.isEmpty() ? upper(sheet.cell(row, col + 1)) : a;
	}

	private static Integer findStart(Sheet sheet, Layout layout, int label, int end) {
		return sheet.row(layout.dayRow()).entrySet().stream()
				.filter(e -> e.getKey() > label && e.getKey() < end)
				.filter(e -> e.getValue().date() && e.getValue().number() != null)
				.filter(e -> weekday(sheet.cell(layout.weekdayRow(), e.getKey())) != null)
				.filter(e -> {
					// the next column is the next day: a lone date with a weekday is not a start
					Integer next = dayNumber(sheet.cell(layout.dayRow(), e.getKey() + 1));
					return next != null && next == e.getValue().dateValue().toLocalDate().plusDays(1).getDayOfMonth();
				})
				.map(Map.Entry::getKey)
				.min(Integer::compare)
				.orElse(null);
	}

	// month / day of the cell (its year is not trusted), year from the bill month
	private static List<LocalDate> firstDayCandidates(LocalDate cell, String weekday, List<YearMonth> billMonths) {
		Set<LocalDate> result = new LinkedHashSet<>();

		for (YearMonth billMonth : billMonths) {
			for (YearMonth month : List.of(billMonth.minusMonths(1), billMonth)) {
				if (month.getMonthValue() != cell.getMonthValue()) {
					continue;
				}

				try {
					LocalDate day = month.atDay(cell.getDayOfMonth());

					if (day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(weekday)) {
						result.add(day);
					}
				} catch (DateTimeException ignored) {
					// Feb 29 of a non leap year
				}
			}
		}

		return new ArrayList<>(result);
	}

	private static YearMonth billMonthOf(LocalDate from, List<YearMonth> billMonths) {
		return billMonths.stream()
				.filter(m -> m.equals(YearMonth.from(from)) || m.minusMonths(1).equals(YearMonth.from(from)))
				.findFirst()
				.orElse(YearMonth.from(from).plusMonths(1));
	}

	private static void parseBlock(
			Sheet sheet,
			Layout layout,
			String scope,
			int start,
			LocalDate from,
			LocalDate to,
			List<ParsedRow> rows,
			List<Issue> errors,
			List<String> warnings,
			int[] skipped
	) {
		String name = sheet.name();
		List<ParsedRow> block = new ArrayList<>();
		int days = (int) Math.min(ChronoUnit.DAYS.between(from, to), MAX_DAYS);

		for (int i = 0; i < days; i++) {
			int col = start + i;
			LocalDate day = from.plusDays(i);
			Cell dayCell = sheet.cell(layout.dayRow(), col);
			Integer dayNumber = dayNumber(dayCell);

			if (dayNumber == null && (dayCell == null || dayCell.isBlank() || dayCell.number() == null)) {
				// shorter period (cut-off change): the block simply ends here
				warnings.add(name + " " + scope + ": block ends at " + day.minusDays(1) + " (" + i + " days, cell "
						+ ref(layout.dayRow(), col) + " has no day)");
				break;
			}

			if (dayNumber == null || dayNumber != day.getDayOfMonth()) {
				// layout changed: nothing of this block is trusted
				errors.add(new Issue(name, day.toString(), scope, ref(layout.dayRow(), col),
						"expected day " + day.getDayOfMonth() + " but found '" + display(dayCell)
								+ "': block not imported"));
				return;
			}

			Cell totalCell = sheet.cell(layout.totalRow(), col);

			if (totalCell == null || totalCell.isBlank()
					|| (totalCell.number() != null && totalCell.number() == 0d)) {
				skipped[0]++;
				continue;
			}

			try {
				BigDecimal total = kwh(totalCell, layout.totalRow(), col, false);
				BigDecimal normal = kwh(sheet.cell(layout.normalRow(), col), layout.normalRow(), col, true);
				BigDecimal high = kwh(sheet.cell(layout.highRow(), col), layout.highRow(), col, true);
				BigDecimal low = kwh(sheet.cell(layout.lowRow(), col), layout.lowRow(), col, true);
				BigDecimal solar = null;

				if (KVH.equals(scope) && layout.solarRow() != null) {
					Cell solarCell = sheet.cell(layout.solarRow(), col);
					solar = solarCell == null || solarCell.isBlank() ? null : kwh(solarCell, layout.solarRow(), col, true);
				}

				block.add(new ParsedRow(name, day, scope, CellReference.convertNumToColString(col),
						layout.normalRow(), layout.totalRow(), normal, high, low, total, solar));
			} catch (IllegalArgumentException e) {
				errors.add(new Issue(name, day.toString(), scope, null, e.getMessage()));
			}
		}

		rows.addAll(block);
	}

	// MWh cell -> kWh (scale 4); blank -> 0 when blankIsZero
	private static BigDecimal kwh(Cell cell, int row, int col, boolean blankIsZero) {
		if (cell == null || cell.isBlank()) {
			if (blankIsZero) {
				return BigDecimal.ZERO.setScale(4);
			}
			throw new IllegalArgumentException(ref(row, col) + " is empty");
		}

		if (cell.error() || cell.number() == null) {
			throw new IllegalArgumentException(ref(row, col) + " is not a number: '" + display(cell) + "'");
		}

		if (cell.number() < 0 || cell.number().isNaN() || cell.number().isInfinite()) {
			throw new IllegalArgumentException(ref(row, col) + " is negative / invalid: " + cell.number());
		}

		return BigDecimal.valueOf(cell.number()).multiply(THOUSAND).setScale(4, RoundingMode.HALF_UP);
	}

	private static Integer dayNumber(Cell cell) {
		if (cell == null || cell.number() == null) {
			return null;
		}

		if (cell.date()) {
			LocalDateTime value = cell.dateValue();
			return value == null ? null : value.getDayOfMonth();
		}

		double value = cell.number();
		return value == Math.rint(value) && value >= 1 && value <= 31 ? (int) value : null;
	}

	private static String weekday(Cell cell) {
		if (cell == null || cell.text() == null) {
			return null;
		}

		String text = cell.text().trim();

		for (DayOfWeek d : DayOfWeek.values()) {
			if (d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(text)) {
				return text;
			}
		}

		return null;
	}

	private static Optional<Month> month(String mon) {
		String lower = mon.toLowerCase(Locale.ROOT);

		for (Month month : Month.values()) {
			if (month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT).equals(lower)) {
				return Optional.of(month);
			}
		}

		return Optional.empty();
	}

	private static String upper(Cell cell) {
		return cell == null || cell.text() == null ? "" : cell.text().trim().toUpperCase(Locale.ROOT);
	}

	private static String display(Cell cell) {
		if (cell == null) {
			return "";
		}

		if (cell.number() != null) {
			return cell.date() ? cell.dateValue().toLocalDate().toString() : String.valueOf(cell.number());
		}

		return cell.text() == null ? "" : cell.text();
	}

	private static String ref(int row, int col) {
		return CellReference.convertNumToColString(col) + row;
	}
}
