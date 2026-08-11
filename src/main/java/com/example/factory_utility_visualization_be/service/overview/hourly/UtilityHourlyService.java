package com.example.factory_utility_visualization_be.service.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.*;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilityHourlyService {

	private static final String DEFAULT_FAC =
			"KVH";

	private static final String DEFAULT_METRIC =
			"Total Energy Consumption";

	private static final BigDecimal DEFAULT_EXCHANGE =
			new BigDecimal("26005");

	private static final BigDecimal DEFAULT_SEPZONE =
			new BigDecimal("1.075");

	private static final Set<String> ALLOWED_FACS =
			Set.of(
					"KVH",
					"Fac_A",
					"Fac_B",
					"Fac_C"
			);

	private final UtilityHourlyRepo repo;


	// ============================================================
	// DASHBOARD BATCH
	// ============================================================

	@Transactional(readOnly = true)
	public UtilityHourlyDashboardDto getHourlyDashboard(
			String facId,
			String nameEn,
			BigDecimal exchange,
			BigDecimal sepzone
	) {

		// ========================================================
		// NORMALIZE INPUT
		// ========================================================

		final String fac =
				normalizeFac(facId);

		final String metric =
				normalizeMetric(nameEn);

		final BigDecimal safeExchange =
				normalizeExchange(exchange);

		final BigDecimal safeSepzone =
				normalizeSepzone(sepzone);


		// ========================================================
		// DATE RANGE
		//
		// yesterday 00:00
		// ->
		// tomorrow 00:00
		// ========================================================

		final LocalDate today =
				LocalDate.now();

		final LocalDate yesterday =
				today.minusDays(1);

		final LocalDateTime fromTime =
				yesterday.atStartOfDay();

		final LocalDateTime toTime =
				today
						.plusDays(1)
						.atStartOfDay();

		final LocalDateTime todayDate =
				today.atStartOfDay();

		final LocalDateTime yesterdayDate =
				yesterday.atStartOfDay();


		// ========================================================
		// 1. ELECTRICITY
		//
		// Không bao gồm Solar
		// ========================================================

		final List<HourlyEnergyCompareProjection>
				electricityRows =
				repo.findHourlyElectricityCompare(
						fac,
						fromTime,
						toTime,
						todayDate,
						yesterdayDate,
						metric,
						safeExchange,
						safeSepzone
				);


		// ========================================================
		// 2. SOLAR
		//
		// Solar money:
		//
		// electricity rate * 83%
		//
		// Query repository đã xử lý phần -17%.
		// ========================================================

		final List<HourlyEnergyCompareProjection>
				solarRows =
				repo.findHourlySolarCompare(
						fac,
						fromTime,
						toTime,
						todayDate,
						yesterdayDate,
						metric,
						safeExchange,
						safeSepzone
				);


		// ========================================================
		// 3. SENSOR
		// ========================================================

		final List<HourlySensorCompareProjection>
				sensorRows =
				repo.findHourlySensorCompare(
						fac,
						fromTime,
						toTime,
						todayDate,
						yesterdayDate
				);


		// ========================================================
		// MAPPING ELECTRICITY
		// ========================================================

		final List<HourlyCompareDto> electricity =
				mapEnergy(electricityRows);


		// ========================================================
		// MAPPING SOLAR
		// ========================================================

		final List<HourlyCompareDto> solar =
				mapEnergy(solarRows);


		// ========================================================
		// MAPPING WATER / AIR
		// ========================================================

		final List<HourlyTempCompareDto> water =
				new ArrayList<>();

		final List<HourlyTempCompareDto> air =
				new ArrayList<>();

		if (sensorRows != null) {

			for (HourlySensorCompareProjection row : sensorRows) {

				if (row == null
						|| row.getScaleHour() == null) {
					continue;
				}

				final HourlyTempCompareDto dto =
						new HourlyTempCompareDto(
								row.getScaleHour(),
								row.getYesterday(),
								row.getToday()
						);

				final String utilityType =
						row.getUtilityType() == null
								? ""
								: row
								.getUtilityType()
								.trim()
								.toUpperCase(Locale.ROOT);

				switch (utilityType) {

					case "WATER" ->
							water.add(dto);

					case "AIR" ->
							air.add(dto);

					default -> {
						// Ignore unknown utility type.
					}
				}
			}
		}


		// ========================================================
		// RESPONSE
		// ========================================================

		return new UtilityHourlyDashboardDto(
				fac,
				LocalDateTime.now(),

				List.copyOf(electricity),
				List.copyOf(solar),

				List.copyOf(water),
				List.copyOf(air)
		);
	}


	// ============================================================
	// SENSOR ONLY
	// ============================================================

	@Transactional(readOnly = true)
	public List<HourlyTempCompareDto>
	getUtilityHourlySensorCompare(
			String facId,
			String type
	) {

		final String normalizedType =
				normalizeSensorType(type);

		final UtilityHourlyDashboardDto dashboard =
				getHourlyDashboard(
						facId,
						DEFAULT_METRIC,
						DEFAULT_EXCHANGE,
						DEFAULT_SEPZONE
				);

		return switch (normalizedType) {

			case "AIR" ->
					dashboard.air();

			default ->
					dashboard.water();
		};
	}


	// ============================================================
	// MAPPING ENERGY
	//
	// Dùng chung cho:
	// - electricity
	// - solar
	// ============================================================

	private List<HourlyCompareDto> mapEnergy(
			List<HourlyEnergyCompareProjection> rows
	) {

		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		return rows.stream()

				.filter(row ->
						row != null
								&& row.getScaleHour() != null
				)

				.map(row ->
						new HourlyCompareDto(

								row.getScaleHour(),

								zeroIfNull(
										row.getYesterday()
								),

								zeroIfNull(
										row.getToday()
								),

								zeroIfNull(
										row.getYesterdayUsd()
								),

								zeroIfNull(
										row.getTodayUsd()
								)
						)
				)

				.toList();
	}


	// ============================================================
	// NULL -> ZERO
	// ============================================================

	private BigDecimal zeroIfNull(
			BigDecimal value
	) {
		return value == null
				? BigDecimal.ZERO
				: value;
	}


	// ============================================================
	// FAC NORMALIZATION
	// ============================================================

	private String normalizeFac(
			String facId
	) {

		if (facId == null
				|| facId.isBlank()) {
			return DEFAULT_FAC;
		}

		final String input =
				facId.trim();

		return ALLOWED_FACS.stream()

				.filter(fac ->
						fac.equalsIgnoreCase(input)
				)

				.findFirst()

				.orElseThrow(() ->
						new IllegalArgumentException(
								"Invalid facId: " + input
						)
				);
	}


	// ============================================================
	// METRIC NORMALIZATION
	// ============================================================

	private String normalizeMetric(
			String nameEn
	) {

		if (nameEn == null
				|| nameEn.isBlank()) {
			return DEFAULT_METRIC;
		}

		return nameEn.trim();
	}


	// ============================================================
	// SENSOR TYPE
	// ============================================================

	private String normalizeSensorType(
			String type
	) {

		if (type == null
				|| type.isBlank()) {
			return "WATER";
		}

		final String normalized =
				type
						.trim()
						.toUpperCase(Locale.ROOT);

		if (!normalized.equals("WATER")
				&& !normalized.equals("AIR")) {

			throw new IllegalArgumentException(
					"type must be WATER or AIR"
			);
		}

		return normalized;
	}


	// ============================================================
	// EXCHANGE
	// ============================================================

	private BigDecimal normalizeExchange(
			BigDecimal exchange
	) {

		if (exchange == null
				|| exchange.compareTo(
				BigDecimal.ZERO
		) <= 0) {

			return DEFAULT_EXCHANGE;
		}

		return exchange;
	}


	// ============================================================
	// SEPZONE
	// ============================================================

	private BigDecimal normalizeSepzone(
			BigDecimal sepzone
	) {

		if (sepzone == null
				|| sepzone.compareTo(
				BigDecimal.ZERO
		) <= 0) {

			return DEFAULT_SEPZONE;
		}

		return sepzone;
	}
}