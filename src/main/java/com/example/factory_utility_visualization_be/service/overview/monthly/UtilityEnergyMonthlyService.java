package com.example.factory_utility_visualization_be.service.overview.monthly;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyQueryRange;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import com.example.factory_utility_visualization_be.service.util.YearMonthParser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UtilityEnergyMonthlyService {

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private final UtilityMonthlyCacheService cacheService;

	public List<MonthlySummaryDto> getMonthlySummary(
			String facId,
			String monthYyyyMm
	) {
		final String fac =
				normalizeFac(facId);

		final YearMonth requestedMonth =
				parseMonth(monthYyyyMm);

		final LocalDateTime now =
				LocalDateTime.now(APP_ZONE);

		final YearMonth currentMonth =
				YearMonth.from(now);

		/*
		 * Không query tháng tương lai.
		 */
		if (requestedMonth.isAfter(currentMonth)) {
			return List.of();
		}

		final MonthlyQueryRange range =
				buildRange(
						requestedMonth,
						now
				);

		if (!range.currentTo().isAfter(range.from())) {
			return List.of();
		}

		if (requestedMonth.equals(currentMonth)) {
			return cacheService.getCurrentMonth(
					fac,
					monthYyyyMm,
					range
			);
		}

		return cacheService.getHistoryMonth(
				fac,
				monthYyyyMm,
				range
		);
	}

	/**
	 * Ép xóa cache của đúng FAC + month,
	 * sau đó query lại DB.
	 */
	public List<MonthlySummaryDto> refreshMonthlySummary(
			String facId,
			String monthYyyyMm
	) {
		final String fac =
				normalizeFac(facId);

		final YearMonth requestedMonth =
				parseMonth(monthYyyyMm);

		final YearMonth currentMonth =
				YearMonth.now(APP_ZONE);

		if (requestedMonth.isAfter(currentMonth)) {
			return List.of();
		}

		if (requestedMonth.equals(currentMonth)) {
			cacheService.evictCurrentMonth(
					fac,
					monthYyyyMm
			);
		} else {
			cacheService.evictHistoryMonth(
					fac,
					monthYyyyMm
			);
		}

		/*
		 * Gọi lại getMonthlySummary để:
		 * 1. query SQL mới
		 * 2. lưu kết quả mới vào cache
		 */
		return getMonthlySummary(
				fac,
				monthYyyyMm
		);
	}

	public void clearAllMonthlyCache() {
		cacheService.clearAll();
	}

	private MonthlyQueryRange buildRange(
			YearMonth requestedMonth,
			LocalDateTime now
	) {
		final LocalDateTime from =
				requestedMonth
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime monthEnd =
				requestedMonth
						.plusMonths(1)
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime currentTo;

		if (now.isBefore(from)) {
			currentTo = from;
		} else if (!now.isBefore(monthEnd)) {
			currentTo = monthEnd;
		} else {
			currentTo = now;
		}

		final YearMonth previousMonth =
				requestedMonth.minusMonths(1);

		final LocalDateTime prevFrom =
				previousMonth
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime prevMonthEnd =
				requestedMonth
						.atDay(1)
						.atStartOfDay();

		final Duration elapsed =
				Duration.between(
						from,
						currentTo
				);

		final LocalDateTime calculatedPrevTo =
				prevFrom.plus(elapsed);

		/*
		 * Tháng hiện tại có thể dài hơn tháng trước.
		 *
		 * Ví dụ:
		 * March 31 ngày
		 * February 28 ngày
		 *
		 * Không cho prevTo vượt đầu tháng hiện tại.
		 */
		final LocalDateTime prevTo =
				calculatedPrevTo.isAfter(prevMonthEnd)
						? prevMonthEnd
						: calculatedPrevTo;

		return new MonthlyQueryRange(
				from,
				currentTo,
				prevFrom,
				prevTo
		);
	}

	private YearMonth parseMonth(
			String monthYyyyMm
	) {
		return YearMonthParser.parse(
				monthYyyyMm,
				"month must use yyyyMM format",
				"Invalid month: "
		);
	}

	private String normalizeFac(
			String facId
	) {
		return FacilityValidator.normalizeOptionalWithDefault(facId);
	}
}