package com.example.factory_utility_visualization_be.service.util;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class YearMonthParser {

	private static final DateTimeFormatter MONTH_FORMATTER =
			DateTimeFormatter.ofPattern("yyyyMM");

	private YearMonthParser() {
	}

	public static YearMonth parse(
			String value,
			String invalidFormatMessage,
			String invalidValueMessagePrefix
	) {
		if (
				value == null
						|| !value.matches("\\d{6}")
		) {
			throw new IllegalArgumentException(
					invalidFormatMessage
			);
		}

		try {

			return YearMonth.parse(
					value,
					MONTH_FORMATTER
			);

		} catch (DateTimeException e) {

			throw new IllegalArgumentException(
					invalidValueMessagePrefix + value,
					e
			);
		}
	}
}
