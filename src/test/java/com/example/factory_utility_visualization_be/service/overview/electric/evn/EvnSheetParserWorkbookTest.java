package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import com.example.factory_utility_visualization_be.service.overview.electric.evn.EvnSheetParser.SheetResult;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.XlsxRowReader.Sheet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Parses every monthly sheet of docs/F2 electric consumption.xlsx (skipped when the file is absent). */
class EvnSheetParserWorkbookTest {

	private static final Path FILE = Path.of("docs", "F2 electric consumption.xlsx");

	@Test
	void parsesEveryMonthlySheet() throws Exception {
		Assumptions.assumeTrue(Files.exists(FILE), "workbook not in docs/");

		List<Sheet> sheets;
		try (InputStream in = Files.newInputStream(FILE)) {
			sheets = XlsxRowReader.read(in, EvnSheetParser::isEvnSheet, EvnSheetParser.FIRST_ROW, EvnSheetParser.LAST_ROW);
		}

		Map<String, SheetResult> results = sheets.stream().collect(Collectors.toMap(Sheet::name,
				s -> EvnSheetParser.parse(s, EvnSheetParser.billMonthCandidates(s.name())),
				(a, b) -> a, java.util.LinkedHashMap::new));

		results.values().forEach(r -> {
			System.out.printf("%-22s block=%-5s bill=%-7s %s..%s rows=%3d skipped=%3d errors=%d warnings=%d%n",
					"'" + r.sheet() + "'", r.hasBlock(), r.billMonth(), r.from(), r.to(), r.rows().size(),
					r.skippedEmpty(), r.errors().size(), r.warnings().size());
			r.errors().forEach(e -> System.out.println("    ERR  " + e));
			r.warnings().forEach(w -> System.out.println("    WARN " + w));
		});

		// old layout, fiscal-year name: FY23-Jan = Jan 2024, period from Dec 21 2023
		SheetResult jan = results.get("FY23-Jan");
		assertThat(jan.billMonth()).isEqualTo(YearMonth.of(2024, 1));
		assertThat(jan.from()).isEqualTo(LocalDate.of(2023, 12, 21));
		assertThat(jan.rows()).isNotEmpty();

		// calendar-year name: FY25-Jan = Jan 2025
		assertThat(results.get("FY25-Jan").billMonth()).isEqualTo(YearMonth.of(2025, 1));

		// cut-off 26 before Nov 2022
		assertThat(results.get("FY22-Sep").from()).isEqualTo(LocalDate.of(2022, 8, 26));

		// solar layout still works
		SheetResult sep = results.get("FY26-Sep + Solar1");
		assertThat(sep.billMonth()).isEqualTo(YearMonth.of(2026, 9));
		assertThat(sep.from()).isEqualTo(LocalDate.of(2026, 8, 21));

		// FY22-Apr .. Aug: no daily EVN block
		assertThat(results.get("FY22-May").hasBlock()).isFalse();
	}
}
