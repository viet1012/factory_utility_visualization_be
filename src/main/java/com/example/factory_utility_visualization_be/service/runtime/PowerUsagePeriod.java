package com.example.factory_utility_visualization_be.service.runtime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Calendar period of GET /api/utility/power-usage. Weeks start on Monday.
 */
public enum PowerUsagePeriod {
	DAY,
	WEEK,
	MONTH;

	// null if value is not day|week|month
	public static PowerUsagePeriod parse(String value) {
		if (value == null || value.isBlank()) {
			return DAY;
		}

		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "day" -> DAY;
			case "week" -> WEEK;
			case "month" -> MONTH;
			default -> null;
		};
	}

	public LocalDateTime start(LocalDate date) {
		return switch (this) {
			case DAY -> date.atStartOfDay();
			case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
			case MONTH -> date.withDayOfMonth(1).atStartOfDay();
		};
	}

	public LocalDateTime end(LocalDateTime start) {
		return switch (this) {
			case DAY -> start.plusDays(1);
			case WEEK -> start.plusWeeks(1);
			case MONTH -> start.plusMonths(1);
		};
	}

	public LocalDateTime previousStart(LocalDateTime start) {
		return switch (this) {
			case DAY -> start.minusDays(1);
			case WEEK -> start.minusWeeks(1);
			case MONTH -> start.minusMonths(1);
		};
	}

	public String label() {
		return name().toLowerCase(Locale.ROOT);
	}
}
