package com.example.factory_utility_visualization_be.service.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.*;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlyEnergyCompareProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlySensorCompareProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UtilityHourlyService {

	private static final String DEFAULT_METRIC =
			"Total Energy Consumption";

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private final UtilityHourlyRepo repo;
	private final ElectricityPricingService pricingService;


	// ============================================================
	// DASHBOARD BATCH
	// ============================================================

	@Transactional(readOnly = true)
	public UtilityHourlyDashboardDto getHourlyDashboard(
			String facId,
			String nameEn
	) {

		// ========================================================
		// NORMALIZE INPUT
		// ========================================================

		final String fac =
				normalizeFac(facId);

		final String metric =
				normalizeMetric(nameEn);


		// ========================================================
		// DATE RANGE
		//
		// yesterday 00:00
		// ->
		// tomorrow 00:00
		// ========================================================

		final LocalDate today =
				LocalDate.now(APP_ZONE);

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
		// Không bao gồm Solar. Giá: ElectricityPricingService (chung
		// cho mọi API điện). Dòng History_Main ghi điện của giờ KẾT
		// THÚC tại pick_at -> cửa sổ query dịch +1h.
		// ========================================================

		final List<HourlyEnergyCompareProjection>
				electricityRows =
				repo.findHourlyElectricityCompare(
						fac,
						fromTime.plusHours(1),
						toTime.plusHours(1),
						todayDate,
						yesterdayDate,
						metric,
						pricingService.gridExchangeRate(),
						pricingService.gridSepzone(),
						pricingService.excludedMeterKeys()
				);


		// ========================================================
		// 2. SENSOR
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

				List.copyOf(sensorSplit.water()),
				List.copyOf(sensorSplit.air())
		);
	}


	// ============================================================
	// SENSOR ROW SPLIT (WATER / AIR)
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
}