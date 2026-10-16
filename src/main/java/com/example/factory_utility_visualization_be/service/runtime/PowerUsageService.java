package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto;
import com.example.factory_utility_visualization_be.exception.PowerUsageBadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * GET /api/utility/power-usage: validates the request and picks the cache.
 * A period counts as ended once its end is CLOSE_GRACE in the past, so the
 * last hourly History_Main rows (written after the hour ends) are in before
 * the result is cached for long.
 */
@Service
@RequiredArgsConstructor
public class PowerUsageService {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final Duration CLOSE_GRACE = Duration.ofHours(2);

	private final PowerUsageCacheService cacheService;

	public PowerUsageResponseDto getPowerUsage(String date, String period) {
		PowerUsagePeriod usagePeriod = PowerUsagePeriod.parse(period);

		if (usagePeriod == null) {
			throw new PowerUsageBadRequestException("period must be one of: day, week, month");
		}

		LocalDateTime now = LocalDateTime.now(APP_ZONE);
		LocalDate day = parseDate(date, now.toLocalDate());

		if (day.isAfter(now.toLocalDate())) {
			throw new PowerUsageBadRequestException("date must not be in the future");
		}

		LocalDateTime periodEnd = usagePeriod.end(usagePeriod.start(day));

		return periodEnd.plus(CLOSE_GRACE).isAfter(now)
				? cacheService.getCurrent(usagePeriod, day)
				: cacheService.getClosed(usagePeriod, day);
	}

	private LocalDate parseDate(String value, LocalDate today) {
		if (value == null || value.isBlank()) {
			return today;
		}

		try {
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new PowerUsageBadRequestException(
					"date must be yyyy-MM-dd, e.g. 2026-10-16: '" + value.trim() + "'");
		}
	}
}
