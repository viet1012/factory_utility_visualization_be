package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;


import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.*;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilitySignalHealthRepo;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilitySignalHealthService {

	private final UtilitySignalHealthRepo repo;

	public List<FacilityHealthDto> getAbnormalSignals() {

		List<UtilityAbnormalSignalProjection> rows =
				repo.findAbnormalSignals();

		return rows.stream()

				// FAC
				.collect(Collectors.groupingBy(
						UtilityAbnormalSignalProjection::getFac
				))

				.entrySet()
				.stream()

				.map(facEntry -> {

					List<CategoryDto> categories = facEntry.getValue()

							// CATEGORY
							.stream()
							.collect(Collectors.groupingBy(
									UtilityAbnormalSignalProjection::getCate
							))

							.entrySet()
							.stream()

							.map(cateEntry -> {

								List<ScadaDto> scadas = cateEntry.getValue()

										// SCADA
										.stream()
										.collect(Collectors.groupingBy(
												UtilityAbnormalSignalProjection::getScadaId
										))

										.entrySet()
										.stream()

										.map(scadaEntry -> {

											List<DeviceDto> devices = scadaEntry.getValue()

													// DEVICE
													.stream()
													.collect(Collectors.groupingBy(
															UtilityAbnormalSignalProjection::getBoxDeviceId
													))

													.entrySet()
													.stream()

													.map(deviceEntry -> {

														List<AbnormalSignalDto> signals =
																deviceEntry.getValue()
																		.stream()
																		.map(this::toSignal)
																		.toList();

														return new DeviceDto(
																deviceEntry.getKey(),
																signals
														);
													})

													.toList();

											return new ScadaDto(
													scadaEntry.getKey(),
													devices
											);
										})

										.toList();

								return new CategoryDto(
										cateEntry.getKey(),
										scadas
								);
							})

							.toList();

					return new FacilityHealthDto(
							facEntry.getKey(),
							categories
					);
				})

				.toList();
	}

	private AbnormalSignalDto toSignal(
			UtilityAbnormalSignalProjection row
	) {

		return new AbnormalSignalDto(

				row.getSignalName(),
				row.getPlcAddress(),

				row.getCurrentValue(),
				row.getPrevValue(),
				row.getJumpSize(),

				row.getStatus(),
				row.getDescription(),

				row.getRecordedAt()
		);
	}
	public List<SignalHealthMatrixDto> getSignalHealthMatrix() {

		List<UtilitySignalHealthMatrixProjection> rows =
				repo.findSignalHealthMatrix();

		return rows.stream()
				.collect(Collectors.groupingBy(
						row ->
								row.getFac() + "|" +
										row.getCate() + "|" +
										row.getScadaId() + "|" +
										row.getBoxDeviceId(),
						LinkedHashMap::new,
						Collectors.toList()
				))
				.values()
				.stream()
				.map(group -> {

					UtilitySignalHealthMatrixProjection first = group.get(0);

					List<SignalHealthMatrixItemDto> signals = group.stream()
							.map(row -> new SignalHealthMatrixItemDto(
									row.getSignalName(),
									row.getUnit(),          // NEW
									row.getPlcAddress(),
									row.getCurrentValue(),
									row.getPrevValue(),
									row.getJumpSize(),
									row.getStatus(),
									row.getDescription(),
									row.getRecordedAt()
							))
							.toList();

					int ngRegisters = (int) signals.stream()
							.filter(signal -> !"OK".equalsIgnoreCase(signal.status()))
							.count();

					return new SignalHealthMatrixDto(
							first.getFac(),
							first.getCate(),
							first.getScadaId(),
							first.getBoxDeviceId(),
							signals.size(),
							ngRegisters,
							ngRegisters > 0 ? "NG" : "OK",
							signals
					);
				})
				.sorted(
						Comparator
								// NG trước, OK sau
								.comparing((SignalHealthMatrixDto dto) ->
										"OK".equalsIgnoreCase(dto.status()) ? 1 : 0
								)

								// Sau đó FAC
								.thenComparing(SignalHealthMatrixDto::fac)

								// Category
								.thenComparing(SignalHealthMatrixDto::cate)

								// SCADA
								.thenComparing(SignalHealthMatrixDto::scadaId)

								// Nhiều NG hơn thì ưu tiên trước
								.thenComparing(
										Comparator.comparingInt(
												SignalHealthMatrixDto::ngRegisters
										).reversed()
								)

								// Cuối cùng BoxDevice
								.thenComparing(SignalHealthMatrixDto::boxDeviceId)
				)
				.toList();
	}

	public byte[] exportAbnormalSignalsExcel() {

		List<UtilityAbnormalSignalProjection> rows = repo.findAbnormalSignals();

		try (Workbook workbook = new XSSFWorkbook();
		     ByteArrayOutputStream out = new ByteArrayOutputStream()) {

			Sheet sheet = workbook.createSheet("Abnormal Signals");

			String[] headers = {
					"No",
					"Facility",
					"Category",
					"SCADA ID",
					"Box Device ID",
					"Signal Name",
					"PLC Address",
					"Previous Value",
					"Current Value",
					"Jump Size",
					"Status",
					"Description",
					"Recorded At"
			};

			CellStyle headerStyle = createHeaderStyle(workbook);
			CellStyle textStyle = createTextStyle(workbook);
			CellStyle numberStyle = createNumberStyle(workbook);
			CellStyle warningStyle = createWarningStyle(workbook);
			CellStyle dateStyle = createDateStyle(workbook);

			Row headerRow = sheet.createRow(0);
			headerRow.setHeightInPoints(28);

			for (int i = 0; i < headers.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(headers[i]);
				cell.setCellStyle(headerStyle);
			}

			int rowIndex = 1;
			int no = 1;

			for (UtilityAbnormalSignalProjection row : rows) {
				Row excelRow = sheet.createRow(rowIndex++);

				createCell(excelRow, 0, no++, numberStyle);
				createCell(excelRow, 1, row.getFac(), textStyle);
				createCell(excelRow, 2, row.getCate(), textStyle);
				createCell(excelRow, 3, row.getScadaId(), numberStyle);
				createCell(excelRow, 4, row.getBoxDeviceId(), textStyle);
				createCell(excelRow, 5, row.getSignalName(), textStyle);
				createCell(excelRow, 6, row.getPlcAddress(), textStyle);

				createCell(excelRow, 7, row.getPrevValue(), numberStyle);
				createCell(excelRow, 8, row.getCurrentValue(), warningStyle);
				createCell(excelRow, 9, row.getJumpSize(), warningStyle);

				createCell(excelRow, 10, row.getStatus(), warningStyle);
				createCell(excelRow, 11, row.getDescription(), textStyle);

				Cell recordedCell = excelRow.createCell(12);
				if (row.getRecordedAt() != null) {
					recordedCell.setCellValue(row.getRecordedAt().toString());
				} else {
					recordedCell.setCellValue("");
				}
				recordedCell.setCellStyle(dateStyle);
			}

			sheet.createFreezePane(0, 1);
			sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
					0,
					Math.max(1, rows.size()),
					0,
					headers.length - 1
			));

			int[] widths = {
					8, 14, 18, 12, 22, 34, 16,
					18, 18, 18, 20, 42, 24
			};

			for (int i = 0; i < headers.length; i++) {
				sheet.setColumnWidth(i, widths[i] * 256);
			}

			workbook.write(out);
			return out.toByteArray();

		} catch (Exception e) {
			throw new RuntimeException("Export abnormal signals Excel failed", e);
		}
	}

	private CellStyle createHeaderStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();

		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.WHITE.getIndex());

		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setAlignment(HorizontalAlignment.CENTER);
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);

		return style;
	}

	private CellStyle createTextStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();

		style.setVerticalAlignment(VerticalAlignment.CENTER);
		style.setWrapText(true);
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);

		return style;
	}

	private CellStyle createNumberStyle(Workbook workbook) {
		CellStyle style = createTextStyle(workbook);

		DataFormat format = workbook.createDataFormat();
		style.setDataFormat(format.getFormat("#,##0.##"));
		style.setAlignment(HorizontalAlignment.RIGHT);

		return style;
	}

	private CellStyle createWarningStyle(Workbook workbook) {
		CellStyle style = createNumberStyle(workbook);

		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.BLACK.getIndex());

		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

		return style;
	}

	private CellStyle createDateStyle(Workbook workbook) {
		CellStyle style = createTextStyle(workbook);
		style.setAlignment(HorizontalAlignment.CENTER);
		return style;
	}

	private void createCell(Row row, int index, Object value, CellStyle style) {
		Cell cell = row.createCell(index);

		if (value == null) {
			cell.setCellValue("");
		} else if (value instanceof Number number) {
			cell.setCellValue(number.doubleValue());
		} else {
			cell.setCellValue(value.toString());
		}

		cell.setCellStyle(style);
	}

}