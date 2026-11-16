package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Cell;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Sheet;
import org.apache.poi.ss.util.CellReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Daily EVN block of one "FY26-<Mon> + Solar1" sheet of F2 electric
 * consumption.xlsx (layout checked on FY26-May .. FY26-Sep):
 *
 *   row 25  block label: "TTL KVH: A&B + C" | "Trans No.1 - Fac C" | "Trans No.2 - Fac A&B"
 *   row 28  weekday (Mon..Sun)
 *   row 29  day: first cell = date (day 21, its YEAR IS OFTEN WRONG -> ignored),
 *           then day numbers, the 1st of the month is a date again
 *   row 30  Normal, 31 High-peak, 32 Low-peak, 33 Total, 34 Total Solar (KVH only), MWh
 *
 * The columns move by one or more every month (KVH starts at BA..BE, Fac C at
 * EF..EP, Fac A&B at HJ..HZ), so the first day column is FOUND, not fixed:
 * the first column after the block label where row 29 is a date with
 * dayOfMonth = first day of the bill period and row 28 is a weekday. The
 * year of every day comes from the sheet name (FY26-Sep -> bill month
 * 2026-09 -> 2026-08-21 .. 2026-09-20); the weekday of the first day must
 * match, so a wrong year is reported instead of imported.
 */
public final class EvnSheetParser {

	public static final int LABEL_ROW = 25;
	public static final int WEEKDAY_ROW = 28;
	public static final int DAY_ROW = 29;
	public static final int NORMAL_ROW = 30;
	public static final int HIGH_ROW = 31;
	public static final int LOW_ROW = 32;
	public static final int TOTAL_ROW = 33;
	public static final int SOLAR_ROW = 34;

	public static final String KVH = "KVH";
	public static final String FAC_AB = "FAC_AB";
	public static final String FAC_C = "FAC_C";

	// FY26-Sep + Solar1, "FY26-May + Solar 1 " (spaces vary)
	private static final Pattern SHEET_NAME = Pattern.compile(
			"^\\s*FY(\\d{2})-([A-Za-z]{3})[A-Za-z]*\\s*\\+\\s*Solar\\s*1\\s*$", Pattern.CASE_INSENSITIVE);

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	private EvnSheetParser() {
	}

	/** One scope x day of the sheet, kWh. */
	public record ParsedRow(
			String sheet,
			LocalDate day,
			String scope,
			// first day column of the block + offset, e.g. "BH"
			String column,
			BigDecimal normalKwh,
			BigDecimal highKwh,
			BigDecimal lowKwh,
			BigDecimal totalKwh,
			// KVH only
			BigDecimal solarKwh
	) {}

	public record Issue(String sheet, String day, String scope, String cell, String message) {}

	public record SheetResult(
			String sheet,
			YearMonth billMonth,
			LocalDate from,
			// exclusive
			LocalDate to,
			// days with total > 0
			List<ParsedRow> rows,
			// scope x day with total empty / 0 (not entered yet)
			int skippedEmpty,
			List<Issue> errors,
			List<String> warnings
	) {}

	public static boolean isEvnSheet(String sheetName) {
		return sheetName != null && SHEET_NAME.matcher(sheetName).matches();
	}

	/** FY26-Sep -> 2026-09 (year = 2000 + FY, as written in the name). */
	public static Optional<YearMonth> billMonth(String sheetName) {
		if (sheetName == null) {
			return Optional.empty();
		}

		Matcher m = SHEET_NAME.matcher(sheetName);

		if (!m.matches()) {
			return Optional.empty();
		}

		String mon = m.group(2).toLowerCase(Locale.ROOT);

		for (Month month : Month.values()) {
			if (month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT).equals(mon)) {
				return Optional.of(YearMonth.of(2000 + Integer.parseInt(m.group(1)), month));
			}
		}

		return Optional.empty();
	}

	/**
	 * @param from first day of the bill period (inclusive)
	 * @param to   first day of the next period (exclusive)
	 */
	public static SheetResult parse(Sheet sheet, YearMonth billMonth, LocalDate from, LocalDate to) {
		List<ParsedRow> rows = new ArrayList<>();
		List<Issue> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		int[] skipped = {0};

		// block labels and boundaries (any block label, solar blocks included)
		TreeMap<Integer, String> boundaries = new TreeMap<>();
		Map<String, Integer> labelCol = new java.util.HashMap<>();

		sheet.row(LABEL_ROW).forEach((col, cell) -> {
			String text = cell.text() == null ? "" : cell.text().trim();
			String upper = text.toUpperCase(Locale.ROOT);

			if (upper.contains("TTL KVH") || upper.startsWith("TRANS NO") || upper.startsWith("SOLAR LOT")) {
				boundaries.put(col, text);
			}

			if (upper.contains("TTL KVH")) {
				labelCol.merge(KVH, col, Math::min);
			} else if (upper.startsWith("TRANS NO") && upper.contains("FAC A&B")) {
				labelCol.merge(FAC_AB, col, Math::min);
			} else if (upper.startsWith("TRANS NO") && upper.contains("FAC C")) {
				labelCol.merge(FAC_C, col, Math::min);
			}
		});

		for (String scope : List.of(KVH, FAC_AB, FAC_C)) {
			Integer label = labelCol.get(scope);

			if (label == null) {
				errors.add(new Issue(sheet.name(), null, scope, "row " + LABEL_ROW,
						"block label not found in row " + LABEL_ROW));
				continue;
			}

			Integer next = boundaries.higherKey(label);
			int end = next == null ? Integer.MAX_VALUE : next;
			Integer start = findStart(sheet, label, end, from);

			if (start == null) {
				errors.add(new Issue(sheet.name(), from.toString(), scope,
						"row " + DAY_ROW + " after " + CellReference.convertNumToColString(label),
						"first day column not found (date with day " + from.getDayOfMonth()
								+ " in row " + DAY_ROW + " and a weekday in row " + WEEKDAY_ROW + ")"));
				continue;
			}

			parseBlock(sheet, scope, start, from, to, rows, errors, skipped);
		}

		checkMonthStartCell(sheet, labelCol.get(KVH), boundaries, from, to, warnings);

		return new SheetResult(sheet.name(), billMonth, from, to, rows, skipped[0], errors, warnings);
	}

	private static Integer findStart(Sheet sheet, int label, int end, LocalDate from) {
		return sheet.row(DAY_ROW).entrySet().stream()
				.filter(e -> e.getKey() > label && e.getKey() < end)
				.filter(e -> e.getValue().date() && e.getValue().number() != null
						&& e.getValue().dateValue().getDayOfMonth() == from.getDayOfMonth())
				.filter(e -> weekday(sheet.cell(WEEKDAY_ROW, e.getKey())) != null)
				.map(Map.Entry::getKey)
				.min(Integer::compare)
				.orElse(null);
	}

	private static void parseBlock(
			Sheet sheet,
			String scope,
			int start,
			LocalDate from,
			LocalDate to,
			List<ParsedRow> rows,
			List<Issue> errors,
			int[] skipped
	) {
		String name = sheet.name();
		String firstWeekday = weekday(sheet.cell(WEEKDAY_ROW, start));
		String expectedWeekday = from.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

		// year comes from the sheet name: the weekday of the first day proves it
		if (!expectedWeekday.equalsIgnoreCase(firstWeekday)) {
			errors.add(new Issue(name, from.toString(), scope, ref(WEEKDAY_ROW, start),
					"weekday '" + firstWeekday + "' but " + from + " is " + expectedWeekday
							+ ": bill month from the sheet name looks wrong (use ?month=yyyy-MM)"));
			return;
		}

		List<ParsedRow> block = new ArrayList<>();
		int days = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to);

		for (int i = 0; i < days; i++) {
			int col = start + i;
			LocalDate day = from.plusDays(i);
			Integer dayNumber = dayNumber(sheet.cell(DAY_ROW, col));

			if (dayNumber == null || dayNumber != day.getDayOfMonth()) {
				// layout changed: nothing of this block is trusted
				errors.add(new Issue(name, day.toString(), scope, ref(DAY_ROW, col),
						"expected day " + day.getDayOfMonth() + " but found '" + display(sheet.cell(DAY_ROW, col))
								+ "': block not imported"));
				return;
			}

			Cell totalCell = sheet.cell(TOTAL_ROW, col);

			if (totalCell == null || totalCell.isBlank()
					|| (totalCell.number() != null && totalCell.number() == 0d)) {
				skipped[0]++;
				continue;
			}

			try {
				BigDecimal total = kwh(totalCell, TOTAL_ROW, col, false);
				BigDecimal normal = kwh(sheet.cell(NORMAL_ROW, col), NORMAL_ROW, col, true);
				BigDecimal high = kwh(sheet.cell(HIGH_ROW, col), HIGH_ROW, col, true);
				BigDecimal low = kwh(sheet.cell(LOW_ROW, col), LOW_ROW, col, true);
				BigDecimal solar = null;

				if (KVH.equals(scope)) {
					Cell solarCell = sheet.cell(SOLAR_ROW, col);
					solar = solarCell == null || solarCell.isBlank() ? null : kwh(solarCell, SOLAR_ROW, col, true);
				}

				block.add(new ParsedRow(name, day, scope, CellReference.convertNumToColString(col),
						normal, high, low, total, solar));
			} catch (IllegalArgumentException e) {
				errors.add(new Issue(name, day.toString(), scope, null, e.getMessage()));
			}
		}

		rows.addAll(block);
	}

	// the 1st of the month cell carries a year too: only a warning when it
	// differs from the sheet name (the name wins, see class doc)
	private static void checkMonthStartCell(
			Sheet sheet,
			Integer kvhLabel,
			TreeMap<Integer, String> boundaries,
			LocalDate from,
			LocalDate to,
			List<String> warnings
	) {
		if (kvhLabel == null) {
			return;
		}

		Integer next = boundaries.higherKey(kvhLabel);
		Integer start = findStart(sheet, kvhLabel, next == null ? Integer.MAX_VALUE : next, from);
		LocalDate first = to.withDayOfMonth(1);

		if (start == null || !first.isAfter(from)) {
			return;
		}

		Cell cell = sheet.cell(DAY_ROW, start + (int) java.time.temporal.ChronoUnit.DAYS.between(from, first));

		if (cell != null && cell.date() && cell.number() != null) {
			LocalDateTime value = cell.dateValue();

			if (value.getYear() != first.getYear() || value.getMonthValue() != first.getMonthValue()) {
				warnings.add(sheet.name() + ": cell " + cell.ref() + " = " + value.toLocalDate()
						+ " but the sheet name gives " + first + " (sheet name used)");
			}
		}
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
			return cell.dateValue().getDayOfMonth();
		}

		double value = cell.number();
		return value == Math.rint(value) ? (int) value : null;
	}

	private static String weekday(Cell cell) {
		if (cell == null || cell.text() == null) {
			return null;
		}

		String text = cell.text().trim();

		for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) {
			if (d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(text)) {
				return text;
			}
		}

		return null;
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
