package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import com.example.factory_utility_visualization_be.dto.overview.electric.EvnImportResultDto;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository;
import com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionCacheEvictor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dry run of the whole docs/F2 electric consumption.xlsx against an empty table (no DB). */
class EvnDailyImportServiceWorkbookTest {

	private static final Path FILE = Path.of("docs", "F2 electric consumption.xlsx");

	@Test
	void dryRunWholeWorkbook() throws Exception {
		Assumptions.assumeTrue(Files.exists(FILE), "workbook not in docs/");

		EvnCalibrationJdbcRepository repo = mock(EvnCalibrationJdbcRepository.class);
		when(repo.findEvnDays(any(), any(), any())).thenReturn(List.of());

		EvnDailyImportService service = new EvnDailyImportService(repo, mock(ElectricConsumptionCacheEvictor.class));
		MockMultipartFile file = new MockMultipartFile("file", FILE.getFileName().toString(),
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.readAllBytes(FILE));

		long t0 = System.currentTimeMillis();
		EvnImportResultDto result = service.importFile(file, null, null, "test", true);

		System.out.printf("%d ms: sheets=%d inserted=%d skipped=%d rejected=%d errors=%d warnings=%d%n",
				System.currentTimeMillis() - t0, result.sheets().size(), result.inserted(), result.skipped(),
				result.rejected(), result.errors().size(), result.warnings().size());
		result.errors().forEach(e -> System.out.println("  ERR  " + e));
		result.warnings().forEach(w -> System.out.println("  WARN " + w));

		// FY22-Sep .. FY26-Sep (FY26-May once)
		assertThat(result.sheets()).hasSize(49);
		assertThat(result.inserted()).isGreaterThan(4000);
		verify(repo, never()).insertEvnDays(anyList());
	}
}
