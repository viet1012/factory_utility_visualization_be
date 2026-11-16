package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardDto;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import com.example.factory_utility_visualization_be.service.overview.electric.DashboardEnergyService;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.Totals;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import com.example.factory_utility_visualization_be.service.util.SolarEnvironmentCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolarDashboardService {

	private static final String POWER_NAME =
			"Total Power";

	private final SolarDashboardRepo repo;
	private final DashboardEnergyService dashboardEnergy;

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

		final long started = System.nanoTime();

		// ============================================================
		// CURRENT POWER (live gauge): light query, latest 'Total Power'
		// of the SOLAR devices only (the month energy sums of
		// getSolarDashboardByMonth are no longer needed, see below)
		// ============================================================
		final BigDecimal currentPower =
				oneDecimal(
						repo.getSolarCurrentPowerKw(
								fac,
								now.plusSeconds(1),
								POWER_NAME
						)
				);
		final long powerNs = System.nanoTime();

		// ============================================================
		// ENERGY: DashboardEnergyService / GridSolarSplit (same as
		// /electric-consumption, hourly data cached per month)
		//
		// per hour: load = Σ sub-meter, grid = max(load - solar, 0)
		// gridKwh  = Σ grid (bought from EVN)
		// totalKwh = Σ load (consumption, solar NOT added on top)
		// share    = solar / load
		// ============================================================
		final Totals energy =
				GridSolarSplit.totals(
						dashboardEnergy.facHours(fac, monthStart, nextMonthStart),
						monthStart,
						nextMonthStart,
						Optional.empty()
				);

		log.info("solar/monthly {} {}: current power {} ms, hourly energy {} ms, total {} ms",
				fac, month,
				(powerNs - started) / 1_000_000,
				(System.nanoTime() - powerNs) / 1_000_000,
				(System.nanoTime() - started) / 1_000_000);

		final BigDecimal solarKwh =
				oneDecimal(energy.solar());

		final BigDecimal gridKwh =
				oneDecimal(energy.grid());

		final BigDecimal totalKwh =
				oneDecimal(energy.load());

		final BigDecimal solarShare =
				oneDecimal(
						energy.load().signum() > 0
								? energy.solar()
										.multiply(BigDecimal.valueOf(100))
										.divide(energy.load(), 6, RoundingMode.HALF_UP)
								: null
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