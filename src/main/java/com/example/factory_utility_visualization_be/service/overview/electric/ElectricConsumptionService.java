package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.exception.ElectricConsumptionBadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * GET /api/utility/electric-consumption: validates the request and picks the
 * cache. A bill period counts as ended once its end is CLOSE_GRACE in the
 * past, so the last hourly History_Main rows are in before it is cached long.
 */
@Service
@RequiredArgsConstructor
public class ElectricConsumptionService {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final Duration CLOSE_GRACE = Duration.ofHours(2);

	private final ElectricConsumptionCalculator calculator;
	private final ElectricConsumptionCacheService cacheService;
	private final ElectricConsumptionProperties properties;

	private static final int MAX_HOUR_OFFSET = 3;

	public ElectricConsumptionResponseDto getElectricConsumption(String month, Integer months, Integer hourOffset) {
		LocalDateTime now = LocalDateTime.now(APP_ZONE);
		YearMonth billMonth = parseMonth(month, calculator.billMonthOf(now.toLocalDate()));
		int monthCount = months == null ? properties.getDefaultMonths() : months;
		int offset = hourOffset == null ? properties.getHourOffset() : hourOffset;

		if (monthCount < 0 || monthCount > properties.getMaxMonths()) {
			throw new ElectricConsumptionBadRequestException(
					"months must be between 0 and " + properties.getMaxMonths());
		}

		if (Math.abs(offset) > MAX_HOUR_OFFSET) {
			throw new ElectricConsumptionBadRequestException(
					"hourOffset must be between -" + MAX_HOUR_OFFSET + " and " + MAX_HOUR_OFFSET);
		}

		ElectricConsumptionCalculator.Period period = calculator.period(billMonth);

		if (period.from().atStartOfDay().isAfter(now)) {
			throw new ElectricConsumptionBadRequestException(
					"bill period of " + billMonth + " has not started yet (starts " + period.from() + ")");
		}

		return period.to().atStartOfDay().plus(CLOSE_GRACE).isAfter(now)
				? cacheService.getCurrent(billMonth, monthCount, offset)
				: cacheService.getClosed(billMonth, monthCount, offset);
	}

	private YearMonth parseMonth(String value, YearMonth defaultMonth) {
		if (value == null || value.isBlank()) {
			return defaultMonth;
		}

		try {
			return YearMonth.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new ElectricConsumptionBadRequestException(
					"month must be yyyy-MM (bill month, e.g. 2026-09 = Aug 21 ~ Sep 20): '" + value.trim() + "'");
		}
	}
}
