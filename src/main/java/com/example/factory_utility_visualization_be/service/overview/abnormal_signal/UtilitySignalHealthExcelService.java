package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class UtilitySignalHealthExcelService {

	private static final String[] HEADERS = {
			"Facility", "Category", "SCADA", "Box Device", "Parameter",
			"Signal Name", "Unit", "PLC Address", "Current Value", "Previous Value",
			"Jump Size", "Status", "Alert", "Description", "Recorded At"
	};
	private static final int[] COLUMN_WIDTHS = {
			16, 18, 16, 18, 18, 30, 12, 18, 16, 16, 14, 12, 10, 42, 22
	};

	private final UtilityMasterDrivenSignalHealthService signalHealthService;

	public UtilitySignalHealthExcelService(UtilityMasterDrivenSignalHealthService signalHealthService) {
		this.signalHealthService = signalHealthService;
	}

	public byte[] export() {
		List<UtilitySignalEvaluation> signals = signalHealthService.evaluateSignals();
		SXSSFWorkbook workbook = new SXSSFWorkbook(100);
		workbook.setCompressTempFiles(true);

		try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ReportStyles styles = createStyles(workbook);
			createSignalSheet(workbook, "Signal Health", signals, styles);
			createSummarySheet(workbook, signals, styles);
			createSignalSheet(workbook, "Abnormal Only",
					signals.stream().filter(UtilitySignalEvaluation::alert).toList(), styles);
			workbook.setActiveSheet(0);
			workbook.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to export utility signal health Excel file", exception);
		} finally {
			workbook.dispose();
		}
	}

	private void createSignalSheet(SXSSFWorkbook workbook, String name,
			List<UtilitySignalEvaluation> signals, ReportStyles styles) {
		Sheet sheet = workbook.createSheet(name);
		writeHeader(sheet, styles.header());
		writeRows(sheet, signals, styles);
		sheet.createFreezePane(0, 1);
		sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, signals.size()), 0, HEADERS.length - 1));
		for (int index = 0; index < COLUMN_WIDTHS.length; index++) {
			sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
		}
	}

	private void createSummarySheet(SXSSFWorkbook workbook, List<UtilitySignalEvaluation> signals,
			ReportStyles styles) {
		Sheet sheet = workbook.createSheet("Summary");
		Row header = sheet.createRow(0);
		createStyledCell(header, 0, "Metric", styles.header());
		createStyledCell(header, 1, "Count", styles.header());

		writeSummaryRow(sheet, 1, "Total Signals", signals.size(), styles);
		writeSummaryRow(sheet, 2, "Total Alerts",
				signals.stream().filter(UtilitySignalEvaluation::alert).count(), styles);
		writeSummaryRow(sheet, 3, "OK count",
				signals.stream().filter(signal -> "OK".equalsIgnoreCase(signal.status())).count(), styles);
		writeSummaryRow(sheet, 4, "NG count",
				signals.stream().filter(signal -> "NG".equalsIgnoreCase(signal.status())).count(), styles);
		sheet.createFreezePane(0, 1);
		sheet.setColumnWidth(0, 24 * 256);
		sheet.setColumnWidth(1, 16 * 256);
	}

	private void writeSummaryRow(Sheet sheet, int rowIndex, String label, long value, ReportStyles styles) {
		CellStyle style = rowIndex % 2 == 0 ? styles.evenText() : styles.oddText();
		Row row = sheet.createRow(rowIndex);
		createStyledCell(row, 0, label, style);
		Cell valueCell = row.createCell(1);
		valueCell.setCellValue(value);
		valueCell.setCellStyle(style);
	}

	private void writeHeader(Sheet sheet, CellStyle style) {
		Row row = sheet.createRow(0);
		row.setHeightInPoints(24);
		for (int index = 0; index < HEADERS.length; index++) {
			createStyledCell(row, index, HEADERS[index], style);
		}
	}

	private void writeRows(Sheet sheet, List<UtilitySignalEvaluation> signals, ReportStyles styles) {
		int rowIndex = 1;
		for (UtilitySignalEvaluation signal : signals) {
			boolean even = rowIndex % 2 == 0;
			CellStyle text = even ? styles.evenText() : styles.oddText();
			CellStyle number = even ? styles.evenNumber() : styles.oddNumber();
			CellStyle date = even ? styles.evenDate() : styles.oddDate();
			CellStyle description = even ? styles.evenDescription() : styles.oddDescription();
			Row row = sheet.createRow(rowIndex++);

			setText(row, 0, signal.fac(), text);
			setText(row, 1, signal.cate(), text);
			setText(row, 2, signal.scadaId(), text);
			setText(row, 3, signal.boxDeviceId(), text);
			setText(row, 4, signal.parameterCode(), text);
			setText(row, 5, signal.signalName(), text);
			setText(row, 6, signal.unit(), text);
			setText(row, 7, signal.plcAddress(), text);
			setNumber(row, 8, signal.currentValue(), number);
			setNumber(row, 9, signal.prevValue(), number);
			setNumber(row, 10, signal.jumpSize(), number);
			setText(row, 11, signal.status(), statusStyle(signal.status(), text, styles));
			setText(row, 12, signal.alert() ? "YES" : "NO", signal.alert() ? styles.alertYes() : text);
			setText(row, 13, signal.description(), description);
			setDate(row, 14, signal.recordedAt(), date);
		}
	}

	private CellStyle statusStyle(String status, CellStyle defaultStyle, ReportStyles styles) {
		if ("NG".equalsIgnoreCase(status)) return styles.statusNg();
		if ("OK".equalsIgnoreCase(status)) return styles.statusOk();
		return defaultStyle;
	}

	private void setText(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		if (value != null) cell.setCellValue(value);
		cell.setCellStyle(style);
	}

	private void setNumber(Row row, int column, Number value, CellStyle style) {
		Cell cell = row.createCell(column);
		if (value != null) cell.setCellValue(value.doubleValue());
		cell.setCellStyle(style);
	}

	private void setDate(Row row, int column, LocalDateTime value, CellStyle style) {
		Cell cell = row.createCell(column);
		if (value != null) cell.setCellValue(value);
		cell.setCellStyle(style);
	}

	private void createStyledCell(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value);
		cell.setCellStyle(style);
	}

	private ReportStyles createStyles(SXSSFWorkbook workbook) {
		CellStyle header = baseStyle(workbook);
		Font headerFont = workbook.createFont();
		headerFont.setBold(true);
		headerFont.setColor(IndexedColors.WHITE.getIndex());
		header.setFont(headerFont);
		header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
		header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		header.setAlignment(HorizontalAlignment.CENTER);

		CellStyle oddText = baseStyle(workbook);
		CellStyle evenText = copyStyle(workbook, oddText);
		evenText.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
		evenText.setFillPattern(FillPatternType.SOLID_FOREGROUND);

		CellStyle oddNumber = copyStyle(workbook, oddText);
		CellStyle evenNumber = copyStyle(workbook, evenText);
		short numberFormat = workbook.createDataFormat().getFormat("#,##0.########");
		oddNumber.setDataFormat(numberFormat);
		evenNumber.setDataFormat(numberFormat);
		oddNumber.setAlignment(HorizontalAlignment.RIGHT);
		evenNumber.setAlignment(HorizontalAlignment.RIGHT);

		CellStyle oddDate = copyStyle(workbook, oddText);
		CellStyle evenDate = copyStyle(workbook, evenText);
		short dateFormat = workbook.createDataFormat().getFormat("dd/MM/yyyy HH:mm:ss");
		oddDate.setDataFormat(dateFormat);
		evenDate.setDataFormat(dateFormat);

		CellStyle oddDescription = copyStyle(workbook, oddText);
		CellStyle evenDescription = copyStyle(workbook, evenText);
		oddDescription.setWrapText(true);
		evenDescription.setWrapText(true);

		return new ReportStyles(header, oddText, evenText, oddNumber, evenNumber, oddDate, evenDate,
				oddDescription, evenDescription,
				highlightedStyle(workbook, IndexedColors.LIGHT_ORANGE, IndexedColors.DARK_RED),
				highlightedStyle(workbook, IndexedColors.ROSE, IndexedColors.DARK_RED),
				highlightedStyle(workbook, IndexedColors.LIGHT_GREEN, IndexedColors.DARK_GREEN));
	}

	private CellStyle highlightedStyle(SXSSFWorkbook workbook, IndexedColors fill, IndexedColors fontColor) {
		CellStyle style = baseStyle(workbook);
		style.setFillForegroundColor(fill.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setAlignment(HorizontalAlignment.CENTER);
		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(fontColor.getIndex());
		style.setFont(font);
		return style;
	}

	private CellStyle baseStyle(SXSSFWorkbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
		style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		return style;
	}

	private CellStyle copyStyle(SXSSFWorkbook workbook, CellStyle source) {
		CellStyle style = workbook.createCellStyle();
		style.cloneStyleFrom(source);
		return style;
	}

	private record ReportStyles(CellStyle header, CellStyle oddText, CellStyle evenText,
			CellStyle oddNumber, CellStyle evenNumber, CellStyle oddDate, CellStyle evenDate,
			CellStyle oddDescription, CellStyle evenDescription, CellStyle alertYes,
			CellStyle statusNg, CellStyle statusOk) {
	}
}
