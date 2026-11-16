package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionViews;
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

	/** ?view= of GET /electric-consumption; null / blank = FULL (unchanged JSON). */
	public enum View {
		// measured + official + calibrated + evn (default)
		FULL,
		// meter data only, EVN / calibration tables not read
		MEASURED,
		// official layer only (EVN when imported, else measured)
		OFFICIAL;

		public static View parse(String value) {
			if (value == null || value.isBlank()) {
				return FULL;
			}

			return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "measured" -> MEASURED;
				case "official" -> OFFICIAL;
				default -> throw new ElectricConsumptionBadRequestException(
						"view must be measured or official (or omitted for every layer): '" + value.trim() + "'");
			};
		}
	}

	/**
	 * FULL -> ElectricConsumptionResponseDto (unchanged), MEASURED ->
	 * ElectricConsumptionViews.MeasuredResponse, OFFICIAL ->
	 * ElectricConsumptionViews.OfficialResponse. FULL and OFFICIAL share the
	 * cached full result; MEASURED has its own entry computed without EVN /
	 * calibration (or reuses a fresh full result of the running period).
	 */
	public Object getElectricConsumption(String month, Integer months, Integer hourOffset, String view) {
		View v = View.parse(view);
		ElectricConsumptionResponseDto full = getElectricConsumption(month, months, hourOffset, v != View.MEASURED);

		return switch (v) {
			case FULL -> full;
			case MEASURED -> ElectricConsumptionViews.MeasuredResponse.of(full);
			case OFFICIAL -> ElectricConsumptionViews.OfficialResponse.of(full);
		};
	}

	public ElectricConsumptionResponseDto getElectricConsumption(String month, Integer months, Integer hourOffset) {
		return getElectricConsumption(month, months, hourOffset, true);
	}

	private ElectricConsumptionResponseDto getElectricConsumption(
			String month, Integer months, Integer hourOffset, boolean withEvn) {
		LocalDateTime now = LocalDateTime.now(APP_ZONE);
		YearMonth billMonth = parseMonth(month, calculator.billMonthOf(now.toLocalDate()));
		int monthCount = months == null ? properties.getDefaultMonths() : months;
		int offset = hourOffset == null ? calculator.defaultHourOffset() : hourOffset;

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
				? cacheService.getCurrent(billMonth, monthCount, offset, withEvn)
				: cacheService.getClosed(billMonth, monthCount, offset, withEvn);
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
