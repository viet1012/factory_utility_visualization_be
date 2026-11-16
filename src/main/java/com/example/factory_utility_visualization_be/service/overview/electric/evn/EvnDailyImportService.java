package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import com.example.factory_utility_visualization_be.dto.overview.electric.EvnImportResultDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.EvnImportResultDto.SheetInfo;
import com.example.factory_utility_visualization_be.exception.EvnCalibrationBadRequestException;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.EvnDay;
import com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionCacheEvictor;
import com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionCalculator;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.EvnSheetParser.Issue;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.EvnSheetParser.ParsedRow;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.EvnSheetParser.SheetResult;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Sheet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * POST /api/utility/evn-daily/import: reads the daily EVN block of the
 * "FY26-<Mon> + Solar1" sheets (EvnSheetParser) and upserts
 * F2_Utility_Evn_Daily by (day, scope). Measured data is never touched.
 *
 * Checks (a failing row is reported and NOT saved, the rest is saved):
 * - Normal + High + Low = Total within +/- 0.5 % of Total
 * - KVH total = FAC_AB total + FAC_C total within +/- 0.5 % of KVH; on
 *   mismatch the 3 rows of that day are rejected (which one is wrong is
 *   unknown); a KVH row without both fac rows is rejected (cannot check)
 * Days with total empty / 0 (not entered yet) are skipped.
 * Two sheets of the same bill month (e.g. "FY26-May + Solar 1" and
 * "FY26-May + Solar 1 ") are both refused: pick one with ?sheet=.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvnDailyImportService {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	// +/- 0.5 %
	private static final BigDecimal TOLERANCE = new BigDecimal("0.005");

	private static final List<String> SCOPES = List.of(EvnSheetParser.KVH, EvnSheetParser.FAC_AB, EvnSheetParser.FAC_C);

	private final EvnCalibrationJdbcRepository repo;
	private final ElectricConsumptionCalculator calculator;
	private final ElectricConsumptionCacheEvictor cacheEvictor;

	@Transactional
	public EvnImportResultDto importFile(
			MultipartFile file,
			// exact sheet name (trimmed), null = every "FY..-<Mon> + Solar1" sheet
			String sheetName,
			// bill month yyyy-MM overriding the sheet name, only with sheetName
			String month,
			String importedBy,
			boolean dryRun
	) {
		String sourceFile = sourceFile(file);
		YearMonth monthOverride = parseMonth(month);

		if (monthOverride != null && (sheetName == null || sheetName.isBlank())) {
			throw new EvnCalibrationBadRequestException("month needs sheet (it overrides the bill month of one sheet)");
		}

		List<Sheet> sheets = read(file, sheetName);

		if (sheets.isEmpty()) {
			throw new EvnCalibrationBadRequestException(sheetName == null || sheetName.isBlank()
					? "no sheet named like 'FY26-<Mon> + Solar1' in " + sourceFile
					: "sheet '" + sheetName.trim() + "' not found in " + sourceFile);
		}

		List<EvnImportResultDto.Error> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<SheetInfo> infos = new ArrayList<>();
		// day -> scope -> row (bill months are unique, so days are unique)
		Map<LocalDate, Map<String, ParsedRow>> byDay = new TreeMap<>();
		int skipped = 0;

		for (Map.Entry<YearMonth, List<Sheet>> entry : byBillMonth(sheets, monthOverride, errors).entrySet()) {
			YearMonth billMonth = entry.getKey();

			if (entry.getValue().size() > 1) {
				String names = entry.getValue().stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(", "));

				for (Sheet s : entry.getValue()) {
					errors.add(new EvnImportResultDto.Error(s.name(), null, null, null,
							"bill month " + billMonth + " is in several sheets (" + names
									+ "): not imported, choose one with ?sheet="));
				}
				continue;
			}

			Sheet sheet = entry.getValue().get(0);
			LocalDate from = calculator.period(billMonth).from();
			LocalDate to = calculator.period(billMonth).to();
			SheetResult result = EvnSheetParser.parse(sheet, billMonth, from, to);

			skipped += result.skippedEmpty();
			warnings.addAll(result.warnings());
			result.errors().forEach(issue -> errors.add(error(issue)));
			infos.add(new SheetInfo(sheet.name(), billMonth.toString(), from.toString(), to.toString(),
					result.rows().size()));

			for (ParsedRow row : result.rows()) {
				byDay.computeIfAbsent(row.day(), key -> new LinkedHashMap<>()).put(row.scope(), row);
			}
		}

		Set<String> rejected = validate(byDay, errors);
		List<ParsedRow> accepted = new ArrayList<>();

		byDay.values().forEach(rows -> rows.values().stream()
				.filter(row -> !rejected.contains(key(row.day(), row.scope())))
				.forEach(accepted::add));

		int[] counts = upsert(accepted, sourceFile, importedBy, dryRun);

		if (!dryRun && counts[0] + counts[1] > 0) {
			// electric-consumption + monthly-summary (official numbers)
			cacheEvictor.evictEvnAfterCommit("EVN import " + sourceFile);
		}

		log.info("EVN import {} (dryRun={}): inserted {}, updated {}, unchanged {}, skipped {}, rejected {}, errors {}",
				sourceFile, dryRun, counts[0], counts[1], counts[2], skipped, rejected.size(), errors.size());

		return new EvnImportResultDto(sourceFile, dryRun, infos, counts[0], counts[1], counts[2], skipped,
				rejected.size(), errors, warnings);
	}

	// ---------------------------------------------------------------------

	private List<Sheet> read(MultipartFile file, String sheetName) {
		String wanted = sheetName == null || sheetName.isBlank() ? null : sheetName.trim();

		try (InputStream in = file.getInputStream()) {
			return XlsxRowReader.read(in,
					name -> wanted == null ? EvnSheetParser.isEvnSheet(name) : name.trim().equals(wanted),
					EvnSheetParser.LABEL_ROW, EvnSheetParser.SOLAR_ROW);
		} catch (IOException e) {
			throw new EvnCalibrationBadRequestException("cannot read the .xlsx file: " + e.getMessage());
		}
	}

	// sheets grouped by bill month (workbook order kept)
	private Map<YearMonth, List<Sheet>> byBillMonth(
			List<Sheet> sheets,
			YearMonth override,
			List<EvnImportResultDto.Error> errors
	) {
		Map<YearMonth, List<Sheet>> byMonth = new LinkedHashMap<>();

		for (Sheet sheet : sheets) {
			YearMonth billMonth = override != null ? override : EvnSheetParser.billMonth(sheet.name()).orElse(null);

			if (billMonth == null) {
				errors.add(new EvnImportResultDto.Error(sheet.name(), null, null, null,
						"bill month not readable from the sheet name (expected 'FY26-Sep + Solar1'), pass ?month=yyyy-MM"));
				continue;
			}

			byMonth.computeIfAbsent(billMonth, key -> new ArrayList<>()).add(sheet);
		}

		return byMonth;
	}

	/** Keys (day|scope) of the rows that fail a check; errors added. */
	private Set<String> validate(Map<LocalDate, Map<String, ParsedRow>> byDay, List<EvnImportResultDto.Error> errors) {
		Set<String> rejected = new LinkedHashSet<>();

		byDay.forEach((day, rows) -> {
			for (ParsedRow row : rows.values()) {
				BigDecimal bands = row.normalKwh().add(row.highKwh()).add(row.lowKwh());

				if (!within(bands, row.totalKwh(), row.totalKwh())) {
					rejected.add(key(day, row.scope()));
					errors.add(new EvnImportResultDto.Error(row.sheet(), day.toString(), row.scope(),
							row.column() + EvnSheetParser.NORMAL_ROW + ":" + row.column() + EvnSheetParser.TOTAL_ROW,
							"Normal + High + Low = " + mwh(bands) + " MWh but Total = " + mwh(row.totalKwh())
									+ " MWh (> 0.5 %)"));
				}
			}

			ParsedRow kvh = rows.get(EvnSheetParser.KVH);

			if (kvh == null) {
				return;
			}

			ParsedRow ab = rows.get(EvnSheetParser.FAC_AB);
			ParsedRow c = rows.get(EvnSheetParser.FAC_C);

			if (ab == null || c == null) {
				rejected.add(key(day, EvnSheetParser.KVH));
				errors.add(new EvnImportResultDto.Error(kvh.sheet(), day.toString(), EvnSheetParser.KVH,
						kvh.column() + EvnSheetParser.TOTAL_ROW,
						"KVH = FAC_AB + FAC_C cannot be checked: " + (ab == null ? "FAC_AB" : "FAC_C")
								+ " total is empty / 0 that day"));
				return;
			}

			BigDecimal facs = ab.totalKwh().add(c.totalKwh());

			if (!within(facs, kvh.totalKwh(), kvh.totalKwh())) {
				for (String scope : SCOPES) {
					rejected.add(key(day, scope));
				}
				errors.add(new EvnImportResultDto.Error(kvh.sheet(), day.toString(), EvnSheetParser.KVH,
						kvh.column() + EvnSheetParser.TOTAL_ROW,
						"KVH total " + mwh(kvh.totalKwh()) + " MWh but FAC_AB + FAC_C = " + mwh(facs)
								+ " MWh (> 0.5 %): KVH, FAC_AB and FAC_C of this day not imported"));
			}
		});

		return rejected;
	}

	// {inserted, updated, unchanged}
	private int[] upsert(List<ParsedRow> rows, String sourceFile, String importedBy, boolean dryRun) {
		int[] counts = new int[3];

		if (rows.isEmpty()) {
			return counts;
		}

		LocalDate min = rows.stream().map(ParsedRow::day).min(LocalDate::compareTo).orElseThrow();
		LocalDate max = rows.stream().map(ParsedRow::day).max(LocalDate::compareTo).orElseThrow();
		Map<String, EvnDay> existing = new HashMap<>();

		for (EvnDay day : repo.findEvnDays(min, max.plusDays(1), null)) {
			existing.put(key(day.day(), day.scope()), day);
		}

		LocalDateTime now = LocalDateTime.now(APP_ZONE).truncatedTo(ChronoUnit.SECONDS);
		String by = importedBy == null || importedBy.isBlank() ? null : truncate(importedBy.trim(), 100);

		for (ParsedRow row : rows) {
			EvnDay value = new EvnDay(row.day(), row.scope(), row.normalKwh(), row.highKwh(), row.lowKwh(),
					row.totalKwh(), row.solarKwh(), sourceFile, now, by);
			EvnDay old = existing.get(key(row.day(), row.scope()));

			if (old == null) {
				counts[0]++;
				if (!dryRun) {
					repo.insertEvnDay(value);
				}
			} else if (!old.sameValues(value)) {
				counts[1]++;
				if (!dryRun) {
					repo.updateEvnDay(value);
				}
			} else {
				counts[2]++;
			}
		}

		return counts;
	}

	// ---------------------------------------------------------------------

	// |value - expected| <= TOLERANCE x base
	private static boolean within(BigDecimal value, BigDecimal expected, BigDecimal base) {
		return value.subtract(expected).abs().compareTo(base.abs().multiply(TOLERANCE)) <= 0;
	}

	private static String key(LocalDate day, String scope) {
		return day + "|" + scope;
	}

	private static EvnImportResultDto.Error error(Issue issue) {
		return new EvnImportResultDto.Error(issue.sheet(), issue.day(), issue.scope(), issue.cell(), issue.message());
	}

	private static String mwh(BigDecimal kwh) {
		return kwh.movePointLeft(3).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private static String sourceFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new EvnCalibrationBadRequestException("file is empty");
		}

		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();

		// keep only the file name (some browsers send a path)
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);

		if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new EvnCalibrationBadRequestException("file must be .xlsx: '" + name + "'");
		}

		return truncate(name, 255);
	}

	private static YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return null;
		}

		try {
			return YearMonth.parse(month.trim());
		} catch (DateTimeParseException e) {
			throw new EvnCalibrationBadRequestException("month must be yyyy-MM: '" + month.trim() + "'");
		}
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
