package com.example.factory_utility_visualization_be.service.overview.electric.evn;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads only some rows of some sheets of an .xlsx with StAX (the F2 workbook
 * has ~60 sheets / 6 MB, XSSFWorkbook would expand all of them in memory).
 * Values are the CACHED cell values (formulas are not evaluated), numbers at
 * full double precision (not the displayed format).
 */
public final class XlsxRowReader {

	/** One cell: number (date cells too, as the Excel serial) or text. */
	public record Cell(String ref, Double number, String text, boolean date, boolean error) {

		public boolean isBlank() {
			return number == null && (text == null || text.isBlank()) && !error;
		}

		public LocalDateTime dateValue() {
			return number == null ? null : DateUtil.getLocalDateTime(number);
		}
	}

	/** rows (1-based) -> column (0-based) -> cell, of one sheet. */
	public record Sheet(String name, Map<Integer, Map<Integer, Cell>> rows) {

		public Cell cell(int row, int col) {
			Map<Integer, Cell> r = rows.get(row);
			return r == null ? null : r.get(col);
		}

		public Map<Integer, Cell> row(int row) {
			return rows.getOrDefault(row, Map.of());
		}
	}

	// F2 electric consumption.xlsx has ~1,800 zip entries (650 charts):
	// over POI's default limit of 1,000 ("file appears to be potentially malicious").
	// The zip-bomb guards (inflate ratio, entry size) stay at their defaults.
	private static final long MAX_ZIP_ENTRIES = 20_000;

	static {
		if (ZipSecureFile.getMaxFileCount() < MAX_ZIP_ENTRIES) {
			ZipSecureFile.setMaxFileCount(MAX_ZIP_ENTRIES);
		}
	}

	private XlsxRowReader() {
	}

	/**
	 * Sheets whose name matches, in workbook order, with rows firstRow..lastRow
	 * (1-based, inclusive).
	 */
	public static List<Sheet> read(InputStream in, Predicate<String> sheetFilter, int firstRow, int lastRow)
			throws IOException {
		try (OPCPackage pkg = OPCPackage.open(in)) {
			XSSFReader reader = new XSSFReader(pkg);
			ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg, false);
			StylesTable styles = reader.getStylesTable();
			XSSFReader.SheetIterator it = (XSSFReader.SheetIterator) reader.getSheetsData();
			List<Sheet> sheets = new ArrayList<>();

			while (it.hasNext()) {
				try (InputStream sheetIn = it.next()) {
					String name = it.getSheetName();

					if (sheetFilter.test(name)) {
						sheets.add(new Sheet(name, readRows(sheetIn, strings, styles, firstRow, lastRow)));
					}
				}
			}

			return sheets;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("cannot read .xlsx: " + e.getMessage(), e);
		}
	}

	private static Map<Integer, Map<Integer, Cell>> readRows(
			InputStream in,
			ReadOnlySharedStringsTable strings,
			StylesTable styles,
			int firstRow,
			int lastRow
	) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

		XMLStreamReader xml = factory.createXMLStreamReader(in);
		Map<Integer, Map<Integer, Cell>> rows = new LinkedHashMap<>();

		try {
			int row = 0;
			String ref = null;
			String type = null;
			int style = -1;
			StringBuilder value = null;
			boolean inValue = false;

			while (xml.hasNext()) {
				int event = xml.next();

				if (event == XMLStreamConstants.START_ELEMENT) {
					String tag = xml.getLocalName();

					switch (tag) {
						case "row" -> {
							String r = xml.getAttributeValue(null, "r");
							row = r == null ? row + 1 : Integer.parseInt(r);

							// rows are written in order: nothing after lastRow is needed
							if (row > lastRow) {
								return rows;
							}
						}
						case "c" -> {
							ref = xml.getAttributeValue(null, "r");
							type = xml.getAttributeValue(null, "t");
							String s = xml.getAttributeValue(null, "s");
							style = s == null ? -1 : Integer.parseInt(s);
							value = new StringBuilder();
						}
						// <v> = value / cached formula result, <t> = inline string text
						case "v", "t" -> inValue = value != null;
						default -> {
						}
					}
				} else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
					if (inValue && value != null) {
						value.append(xml.getText());
					}
				} else if (event == XMLStreamConstants.END_ELEMENT) {
					String tag = xml.getLocalName();

					if ("v".equals(tag) || "t".equals(tag)) {
						inValue = false;
					} else if ("c".equals(tag)) {
						if (row >= firstRow && ref != null) {
							Cell cell = toCell(ref, type, style, value == null ? "" : value.toString(), strings, styles);
							int col = new CellReference(ref).getCol();
							rows.computeIfAbsent(row, key -> new HashMap<>()).put(col, cell);
						}
						value = null;
					}
				}
			}

			return rows;
		} finally {
			xml.close();
		}
	}

	private static Cell toCell(
			String ref,
			String type,
			int style,
			String raw,
			ReadOnlySharedStringsTable strings,
			StylesTable styles
	) {
		if (raw.isEmpty()) {
			return new Cell(ref, null, null, false, false);
		}

		if (type == null || "n".equals(type)) {
			double number;

			try {
				number = Double.parseDouble(raw);
			} catch (NumberFormatException e) {
				return new Cell(ref, null, raw, false, true);
			}

			return new Cell(ref, number, null, isDateStyle(style, styles), false);
		}

		return switch (type) {
			case "s" -> new Cell(ref, null, strings.getItemAt(Integer.parseInt(raw)).getString(), false, false);
			case "str", "inlineStr" -> new Cell(ref, null, raw, false, false);
			case "e" -> new Cell(ref, null, raw, false, true);
			// booleans are not used by the sheet
			default -> new Cell(ref, null, raw, false, false);
		};
	}

	private static boolean isDateStyle(int style, StylesTable styles) {
		if (style < 0 || styles == null || style >= styles.getNumCellStyles()) {
			return false;
		}

		XSSFCellStyle cellStyle = styles.getStyleAt(style);
		return cellStyle != null && DateUtil.isADateFormat(cellStyle.getDataFormat(), cellStyle.getDataFormatString());
	}
}
