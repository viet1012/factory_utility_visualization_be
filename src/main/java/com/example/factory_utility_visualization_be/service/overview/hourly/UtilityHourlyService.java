package com.example.factory_utility_visualization_be.service.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.*;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlyEnergyCompareProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlySensorCompareProjection;
import com.example.factory_utility_visualization_be.config.UtilityFinanceProperties;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UtilityHourlyService {

	private static final String DEFAULT_METRIC =
			"Total Energy Consumption";

	private final UtilityHourlyRepo repo;
	private final UtilityFinanceProperties financeProperties;


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

		final SensorSplit sensorSplit =
				splitSensorRows(sensorRows);


		// ========================================================
		// RESPONSE
		// ========================================================

		return new UtilityHourlyDashboardDto(
				fac,
				LocalDateTime.now(),

				List.copyOf(electricity),
				List.copyOf(solar),

				List.copyOf(sensorSplit.water()),
				List.copyOf(sensorSplit.air())
		);
	}


	// ============================================================
	// SENSOR ONLY
	//
	// Chỉ gọi findHourlySensorCompare — không chạy electricity/solar.
	// ============================================================

	@Transactional(readOnly = true)
	public List<HourlyTempCompareDto>
	getUtilityHourlySensorCompare(
			String facId,
			String type
	) {

		final String normalizedType =
				normalizeSensorType(type);

		final String fac =
				normalizeFac(facId);

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

		final List<HourlySensorCompareProjection>
				sensorRows =
				repo.findHourlySensorCompare(
						fac,
						fromTime,
						toTime,
						todayDate,
						yesterdayDate
				);

		final SensorSplit sensorSplit =
				splitSensorRows(sensorRows);

		return switch (normalizedType) {

			case "AIR" ->
					List.copyOf(sensorSplit.air());

			default ->
					List.copyOf(sensorSplit.water());
		};
	}


	// ============================================================
	// SENSOR ROW SPLIT (WATER / AIR)
	//
	// Dùng chung cho:
	// - getHourlyDashboard
	// - getUtilityHourlySensorCompare
	// ============================================================

	private SensorSplit splitSensorRows(
			List<HourlySensorCompareProjection> sensorRows
	) {

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

		return new SensorSplit(water, air);
	}


	// ============================================================
	// SENSOR SPLIT HOLDER
	// ============================================================

	private record SensorSplit(
			List<HourlyTempCompareDto> water,
			List<HourlyTempCompareDto> air
	) {
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
		return FacilityValidator.normalizeOptionalWithDefault(facId);
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

			return financeProperties.getHourly().getExchangeRate();
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

			return financeProperties.getHourly().getSepzone();
		}

		return sepzone;
	}
}