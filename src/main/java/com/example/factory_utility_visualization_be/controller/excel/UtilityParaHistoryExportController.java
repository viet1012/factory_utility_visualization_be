package com.example.factory_utility_visualization_be.controller.excel;


import com.example.factory_utility_visualization_be.service.excel.UtilityParaHistoryExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/utility/history")
@RequiredArgsConstructor
public class UtilityParaHistoryExportController {

	private final UtilityParaHistoryExportService exportService;

	@GetMapping("/export-excel-by-hours")
	public ResponseEntity<byte[]> exportExcelByHours(
			@RequestParam String boxDeviceId,

			@RequestParam
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate fromDate,

			@RequestParam
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate toDate,

			@RequestParam List<Integer> hours
	) {
		byte[] excel = exportService.exportExcelByHours(
				boxDeviceId,
				fromDate,
				toDate,
				hours
		);

		String filename = "utility_history_" +
				boxDeviceId + "_" +
				fromDate + "_to_" + toDate + ".xlsx";

		return ResponseEntity.ok()
				.header(
						HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + filename + "\""
				)
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				))
				.body(excel);
	}
}
