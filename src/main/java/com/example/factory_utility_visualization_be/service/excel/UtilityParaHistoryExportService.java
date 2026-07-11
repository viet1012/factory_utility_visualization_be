package com.example.factory_utility_visualization_be.service.excel;


import com.example.factory_utility_visualization_be.model.F2UtilityParaHistory;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaHistoryRepo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
@Service
@RequiredArgsConstructor
public class UtilityParaHistoryExportService {

	private final F2UtilityParaHistoryRepo repo;

	public byte[] exportExcelByHours(
			String boxDeviceId,
			LocalDate fromDate,
			LocalDate toDate,
			List<Integer> hours
	) {
		LocalDateTime from = fromDate.atStartOfDay();
		LocalDateTime to = toDate.atTime(23, 59, 59);

		List<F2UtilityParaHistory> rows =
				repo.findByBoxDeviceIdAndDateRangeAndHours(
						boxDeviceId,
						from,
						to,
						hours,
						59
				);

		return buildExcel(rows, "Utility History By Hours");
	}private byte[] buildExcel(List<F2UtilityParaHistory> rows, String sheetName) {
		try (
				Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream out = new ByteArrayOutputStream()
		) {
			Sheet sheet = workbook.createSheet(sheetName);

			CellStyle headerStyle = workbook.createCellStyle();
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerStyle.setFont(headerFont);

			Row header = sheet.createRow(0);
			String[] columns = {
					"ID",
					"Box Device ID",
					"PLC Address",
					"Value",
					"Recorded At"
			};

			for (int i = 0; i < columns.length; i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(columns[i]);
				cell.setCellStyle(headerStyle);
			}

			DateTimeFormatter formatter =
					DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

			int rowIndex = 1;

			for (F2UtilityParaHistory item : rows) {
				Row row = sheet.createRow(rowIndex++);

				row.createCell(0).setCellValue(item.getId());
				row.createCell(1).setCellValue(item.getBoxDeviceId());
				row.createCell(2).setCellValue(item.getPlcAddress());

				if (item.getValue() != null) {
					row.createCell(3).setCellValue(item.getValue().doubleValue());
				} else {
					row.createCell(3).setCellValue("");
				}

				row.createCell(4).setCellValue(
						item.getRecordedAt() == null
								? ""
								: item.getRecordedAt().format(formatter)
				);
			}

			for (int i = 0; i < columns.length; i++) {
				sheet.autoSizeColumn(i);
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			throw new RuntimeException("Export utility history excel failed", e);
		}
	}
}