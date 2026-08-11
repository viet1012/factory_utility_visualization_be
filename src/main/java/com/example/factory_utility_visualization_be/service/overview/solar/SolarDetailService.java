package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.*;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SolarDetailService {

	private static final String ENERGY_NAME =
			"Total Energy Consumption";

	private static final String POWER_NAME =
			"Total Power";

	private static final BigDecimal EXCHANGE =
			new BigDecimal("26005");

	private static final BigDecimal SEPZONE =
			new BigDecimal("1.075");

	private static final BigDecimal CO2_FACTOR =
			new BigDecimal("0.6766");

	private static final BigDecimal KG_PER_TREE =
			new BigDecimal("21");

	private final SolarDashboardRepo repo;

	@Transactional(readOnly = true)
	public SolarDetailDto getDetail(
			String facId,
			String month
	) {

		final String fac =
				normalizeFac(facId);

		final YearMonth ym =
				YearMonth.parse(
						month,
						DateTimeFormatter.ofPattern("yyyyMM")
				);

		final LocalDateTime monthStart =
				ym.atDay(1)
						.atStartOfDay();

		final LocalDateTime nextMonthStart =
				ym.plusMonths(1)
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime now =
				LocalDateTime.now();

		// =========================================================
		// SUMMARY
		// =========================================================

		final SolarDashboardProjection summaryRow =
				repo.getSolarDashboardByMonth(
						fac,
						monthStart,
						nextMonthStart,
						now.plusSeconds(1),
						POWER_NAME,
						ENERGY_NAME
				);

		final BigDecimal solarKwh =
				value(
						summaryRow == null
								? null
								: summaryRow.getSolarKwh()
				);

		final SolarDetailSummaryDto summary =
				new SolarDetailSummaryDto(

						value(
								summaryRow == null
										? null
										: summaryRow.getCurrentPowerKw()
						),

						solarKwh,

						value(
								summaryRow == null
										? null
										: summaryRow.getGridKwh()
						),

						value(
								summaryRow == null
										? null
										: summaryRow.getTotalKwh()
						),

						value(
								summaryRow == null
										? null
										: summaryRow.getSolarSharePercent()
						)
				);


		// =========================================================
		// DAILY TREND
		// =========================================================

		final List<SolarDailyTrendDto> dailyTrend =
				repo.getSolarDailyTrend(
								fac,
								monthStart,
								nextMonthStart,
								ENERGY_NAME
						)
						.stream()
						.map(row ->
								new SolarDailyTrendDto(
										row.getRecordDate(),
										value(row.getSolarKwh()),
										value(row.getGridKwh()),
										value(row.getTotalKwh()),
										value(row.getSolarSharePercent())
								)
						)
						.toList();


		// =========================================================
		// COST
		// =========================================================

		final SolarCostProjection costRow =
				repo.getSolarMonthlyCost(
						fac,
						monthStart,
						nextMonthStart,
						ENERGY_NAME
				);

		final BigDecimal normalVnd =
				value(
						costRow == null
								? null
								: costRow.getNormalCostVnd()
				);

		final BigDecimal solarVnd =
				value(
						costRow == null
								? null
								: costRow.getSolarCostVnd()
				);

		final BigDecimal savingVnd =
				value(
						costRow == null
								? null
								: costRow.getSavingVnd()
				);

		final SolarCostImpactDto costImpact =
				new SolarCostImpactDto(

						value(
								costRow == null
										? null
										: costRow.getSolarEnergyKwh()
						),

						normalVnd,
						solarVnd,
						savingVnd,

						toUsd(normalVnd),
						toUsd(solarVnd),
						toUsd(savingVnd),

						new BigDecimal("17.0")
				);


		// =========================================================
		// ENVIRONMENT
		// =========================================================

		final BigDecimal co2Kg =
				solarKwh
						.multiply(CO2_FACTOR)
						.setScale(
								1,
								RoundingMode.HALF_UP
						);

		final SolarEnvironmentalDto environment =
				new SolarEnvironmentalDto(

						co2Kg,

						co2Kg.divide(
								new BigDecimal("1000"),
								3,
								RoundingMode.HALF_UP
						),

						co2Kg.divide(
								KG_PER_TREE,
								0,
								RoundingMode.HALF_UP
						),

						CO2_FACTOR
				);


		// =========================================================
		// HOURLY PROFILE
		//
		// Nếu đang xem tháng hiện tại -> hôm nay.
		// Nếu tháng cũ -> lấy ngày cuối cùng có data.
		// =========================================================

		LocalDate profileDate;

		final YearMonth currentYm =
				YearMonth.from(now);

		if (ym.equals(currentYm)) {
			profileDate = now.toLocalDate();
		} else if (!dailyTrend.isEmpty()) {
			profileDate =
					dailyTrend
							.get(dailyTrend.size() - 1)
							.date();
		} else {
			profileDate =
					ym.atEndOfMonth();
		}

		final LocalDateTime dayStart =
				profileDate.atStartOfDay();

		final LocalDateTime nextDayStart =
				dayStart.plusDays(1);

		final List<SolarHourlyPointDto> hourlyPoints =
				repo.getSolarHourlyProfile(
								fac,
								dayStart,
								nextDayStart,
								ENERGY_NAME
						)
						.stream()
						.map(row ->
								new SolarHourlyPointDto(
										row.getScaleHour(),
										value(row.getEnergyKwh())
								)
						)
						.toList();

		final BigDecimal totalHourly =
				hourlyPoints
						.stream()
						.map(SolarHourlyPointDto::energyKwh)
						.reduce(
								BigDecimal.ZERO,
								BigDecimal::add
						);

		final SolarHourlyPointDto peak =
				hourlyPoints
						.stream()
						.max(
								Comparator.comparing(
										SolarHourlyPointDto::energyKwh
								)
						)
						.orElse(null);

		final SolarHourlyProfileDto hourlyProfile =
				new SolarHourlyProfileDto(

						profileDate,

						totalHourly,

						peak == null
								? BigDecimal.ZERO
								: peak.energyKwh(),

						peak == null
								? null
								: peak.hour(),

						hourlyPoints
				);


		return new SolarDetailDto(
				fac,
				month,
				now,
				summary,
				dailyTrend,
				costImpact,
				environment,
				hourlyProfile
		);
	}


	private BigDecimal toUsd(
			BigDecimal vnd
	) {

		if (vnd == null) {
			return BigDecimal.ZERO;
		}

		return vnd
				.divide(
						EXCHANGE,
						6,
						RoundingMode.HALF_UP
				)
				.multiply(SEPZONE)
				.setScale(
						2,
						RoundingMode.HALF_UP
				);
	}


	private BigDecimal value(
			BigDecimal value
	) {

		return value == null
				? BigDecimal.ZERO
				: value;
	}


	private String normalizeFac(
			String facId
	) {

		if (facId == null ||
				facId.isBlank()) {
			return "KVH";
		}

		if (facId.equalsIgnoreCase("FAC_A")) {
			return "Fac_A";
		}

		if (facId.equalsIgnoreCase("FAC_B")) {
			return "Fac_B";
		}

		if (facId.equalsIgnoreCase("FAC_C")) {
			return "Fac_C";
		}

		if (facId.equalsIgnoreCase("KVH")) {
			return "KVH";
		}

		return facId.trim();
	}
}