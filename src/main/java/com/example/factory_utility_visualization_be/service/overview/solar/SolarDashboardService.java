package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardDto;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import com.example.factory_utility_visualization_be.service.util.SolarEnvironmentCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SolarDashboardService {

	private static final String POWER_NAME =
			"Total Power";

	private static final String ENERGY_NAME =
			"Total Energy Consumption";

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private final SolarDashboardRepo repo;
	private final SolarMonthlySummaryCacheService summaryCache;

	@Transactional(readOnly = true)
	public SolarDashboardDto getDashboard(
			String facId
	) {
		final String fac =
				normalizeFac(
						facId
				);

		final LocalDateTime now =
				LocalDateTime.now();

		final LocalDateTime todayStart =
				now.toLocalDate()
						.atStartOfDay();

		final LocalDateTime tomorrowStart =
				todayStart.plusDays(1);

		final SolarDashboardProjection p =
				repo.getSolarDashboardByToday(
						fac,
						todayStart,
						tomorrowStart,
						now.plusSeconds(1),
						POWER_NAME,
						ENERGY_NAME
				);

		final BigDecimal currentPower =
				oneDecimal(
						p == null
								? null
								: p.getCurrentPowerKw()
				);

		final BigDecimal solarKwh =
				oneDecimal(
						p == null
								? null
								: p.getSolarKwh()
				);

		final BigDecimal gridKwh =
				oneDecimal(
						p == null
								? null
								: p.getGridKwh()
				);

		final BigDecimal totalKwh =
				oneDecimal(
						p == null
								? null
								: p.getTotalKwh()
				);

		final BigDecimal solarShare =
				oneDecimal(
						p == null
								? null
								: p.getSolarSharePercent()
				);

		// CO2 chỉ tính từ điện Solar TODAY
		final BigDecimal co2Kg =
				SolarEnvironmentCalculator.co2Kg(solarKwh);

		final BigDecimal co2Ton =
				SolarEnvironmentCalculator.co2Ton(co2Kg);

		final BigDecimal trees =
				SolarEnvironmentCalculator.equivalentTrees(co2Kg);

		return new SolarDashboardDto(
				fac,
				now,

				currentPower,

				solarKwh,
				gridKwh,
				totalKwh,

				solarShare,

				co2Kg,
				co2Ton,
				trees
		);
	}

	@Transactional(readOnly = true)
	public SolarDashboardDto getSolarDashboardByMonth(
			String facId,
			String month
	) {

		final String fac =
				normalizeFac(facId);

		// ============================================================
		// PARSE MONTH
		//
		// VD:
		// 202608 -> 2026-08-01
		// ============================================================
		final LocalDate selectedMonth =
				parseMonth(month);

		final LocalDateTime monthStart =
				selectedMonth.atStartOfDay();

		final LocalDateTime nextMonthStart =
				selectedMonth
						.plusMonths(1)
						.atStartOfDay();

		final LocalDateTime now =
				LocalDateTime.now();

		// ============================================================
		// QUERY
		//
		// Current month: always fresh, never cached (accumulating MTD
		// totals + live current power). Completed months: cached, since
		// the query window is closed and the result cannot change.
		// ============================================================
		final YearMonth requestedMonth =
				YearMonth.from(selectedMonth);

		final SolarDashboardProjection p =
				requestedMonth.equals(YearMonth.now(APP_ZONE))
						? repo.getSolarDashboardByMonth(
								fac,
								monthStart,
								nextMonthStart,
								now.plusSeconds(1),
								POWER_NAME,
								ENERGY_NAME
						)
						: summaryCache.getHistoricalSummary(
								fac,
								requestedMonth.toString(),
								monthStart,
								nextMonthStart,
								now.plusSeconds(1),
								POWER_NAME,
								ENERGY_NAME
						);

		// ============================================================
		// MAPPING
		// ============================================================
		final BigDecimal currentPower =
				oneDecimal(
						p == null
								? null
								: p.getCurrentPowerKw()
				);

		final BigDecimal solarKwh =
				oneDecimal(
						p == null
								? null
								: p.getSolarKwh()
				);

		final BigDecimal gridKwh =
				oneDecimal(
						p == null
								? null
								: p.getGridKwh()
				);

		final BigDecimal totalKwh =
				oneDecimal(
						p == null
								? null
								: p.getTotalKwh()
				);

		final BigDecimal solarShare =
				oneDecimal(
						p == null
								? null
								: p.getSolarSharePercent()
				);

		// ============================================================
		// CO2 THEO SOLAR CỦA THÁNG
		//
		// CO2 kg = Solar kWh * 0.6766
		// ============================================================
		final BigDecimal co2Kg =
				SolarEnvironmentCalculator.co2Kg(solarKwh);

		// ============================================================
		// CO2 TON
		// ============================================================
		final BigDecimal co2Ton =
				SolarEnvironmentCalculator.co2Ton(co2Kg);

		// ============================================================
		// EQUIVALENT TREES
		// ============================================================
		final BigDecimal trees =
				SolarEnvironmentCalculator.equivalentTrees(co2Kg);

		// ============================================================
		// RESPONSE
		// ============================================================
		return new SolarDashboardDto(
				fac,
				now,

				currentPower,

				solarKwh,
				gridKwh,
				totalKwh,

				solarShare,

				co2Kg,
				co2Ton,
				trees
		);
	}

	private LocalDate parseMonth(String month) {

		// Không truyền month -> tháng hiện tại
		if (month == null || month.isBlank()) {
			return LocalDate.now()
					.withDayOfMonth(1);
		}

		final String value =
				month.trim();

		// yyyyMM
		if (!value.matches("\\d{6}")) {
			throw new IllegalArgumentException(
					"month must be yyyyMM. Example: 202608"
			);
		}

		final int year =
				Integer.parseInt(
						value.substring(0, 4)
				);

		final int monthValue =
				Integer.parseInt(
						value.substring(4, 6)
				);

		if (monthValue < 1 || monthValue > 12) {
			throw new IllegalArgumentException(
					"Invalid month: " + month
			);
		}

		return LocalDate.of(
				year,
				monthValue,
				1
		);
	}

	private BigDecimal oneDecimal(
			BigDecimal value
	) {
		return value == null
				? BigDecimal.ZERO.setScale(
				1,
				RoundingMode.HALF_UP
		)
				: value.setScale(
				1,
				RoundingMode.HALF_UP
		);
	}

	private String normalizeFac(
			String facId
	) {
		return FacilityValidator.normalizeOptionalWithDefault(facId);
	}
}