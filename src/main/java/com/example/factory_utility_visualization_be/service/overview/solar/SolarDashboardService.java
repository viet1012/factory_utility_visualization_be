package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SolarDashboardService {

	private static final String DEFAULT_FAC =
			"KVH";

	private static final String POWER_NAME =
			"Total Power";

	private static final String ENERGY_NAME =
			"Total Energy Consumption";

	private static final BigDecimal CO2_FACTOR =
			new BigDecimal("0.6766");

	private static final BigDecimal KG_PER_TREE =
			new BigDecimal("21");

	private final SolarDashboardRepo repo;

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
				solarKwh
						.multiply(
								CO2_FACTOR
						)
						.setScale(
								1,
								RoundingMode.HALF_UP
						);

		final BigDecimal co2Ton =
				co2Kg
						.divide(
								new BigDecimal("1000"),
								3,
								RoundingMode.HALF_UP
						);

		final BigDecimal trees =
				co2Kg
						.divide(
								KG_PER_TREE,
								0,
								RoundingMode.HALF_UP
						);

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
		// ============================================================
		final SolarDashboardProjection p =
				repo.getSolarDashboardByMonth(
						fac,
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
				solarKwh
						.multiply(CO2_FACTOR)
						.setScale(
								1,
								RoundingMode.HALF_UP
						);

		// ============================================================
		// CO2 TON
		// ============================================================
		final BigDecimal co2Ton =
				co2Kg
						.divide(
								new BigDecimal("1000"),
								3,
								RoundingMode.HALF_UP
						);

		// ============================================================
		// EQUIVALENT TREES
		// ============================================================
		final BigDecimal trees =
				co2Kg
						.divide(
								KG_PER_TREE,
								0,
								RoundingMode.HALF_UP
						);

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
		if (
				facId == null
						|| facId.isBlank()
		) {
			return DEFAULT_FAC;
		}

		final String fac =
				facId.trim();

		if (
				fac.equalsIgnoreCase(
						"KVH"
				)
		) {
			return "KVH";
		}

		if (
				fac.equalsIgnoreCase(
						"FAC_A"
				)
		) {
			return "Fac_A";
		}

		if (
				fac.equalsIgnoreCase(
						"FAC_B"
				)
		) {
			return "Fac_B";
		}

		if (
				fac.equalsIgnoreCase(
						"FAC_C"
				)
		) {
			return "Fac_C";
		}

		return fac;
	}
}