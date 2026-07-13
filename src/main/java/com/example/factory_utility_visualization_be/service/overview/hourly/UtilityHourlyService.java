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

	private static final String DEFAULT_FAC = "KVH";

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
		final String fac = normalizeFac(facId);
		final String metric = normalizeMetric(nameEn);

		final BigDecimal safeExchange =
				normalizeExchange(exchange);

		final BigDecimal safeSepzone =
				normalizeSepzone(sepzone);

		final LocalDate today = LocalDate.now();
		final LocalDate yesterday = today.minusDays(1);

		final LocalDateTime fromTime =
				yesterday.atStartOfDay();

		final LocalDateTime toTime =
				today.plusDays(1).atStartOfDay();

		final LocalDateTime todayDate =
				today.atStartOfDay();

		final LocalDateTime yesterdayDate =
				yesterday.atStartOfDay();

		final List<HourlyEnergyCompareProjection>
				energyRows =
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

		final List<HourlySensorCompareProjection>
				sensorRows =
				repo.findHourlySensorCompare(
						fac,
						fromTime,
						toTime,
						todayDate,
						yesterdayDate
				);

		final List<HourlyCompareDto> electricity =
				mapElectricity(energyRows);

		final List<HourlyTempCompareDto> water =
				new ArrayList<>();

		final List<HourlyTempCompareDto> air =
				new ArrayList<>();

		for (HourlySensorCompareProjection row : sensorRows) {
			if (row.getScaleHour() == null) {
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
							: row.getUtilityType()
							.trim()
							.toUpperCase(Locale.ROOT);

			switch (utilityType) {
				case "WATER" -> water.add(dto);
				case "AIR" -> air.add(dto);
				default -> {
					// Ignore unknown utility type.
				}
			}
		}

		return new UtilityHourlyDashboardDto(
				fac,
				LocalDateTime.now(),
				List.copyOf(electricity),
				List.copyOf(water),
				List.copyOf(air)
		);
	}

	// ============================================================
	// OPTIONAL OLD ENDPOINT SUPPORT
	// ============================================================

	@Transactional(readOnly = true)
	public List<HourlyCompareDto> getHourly(
			String facId,
			int hours,
			String nameEn,
			BigDecimal exchange,
			BigDecimal sepzone
	) {
		final UtilityHourlyDashboardDto dashboard =
				getHourlyDashboard(
						facId,
						nameEn,
						exchange,
						sepzone
				);

		return dashboard.electricity();
	}

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
			case "AIR" -> dashboard.air();
			default -> dashboard.water();
		};
	}

	// ============================================================
	// MAPPING
	// ============================================================

	private List<HourlyCompareDto> mapElectricity(
			List<HourlyEnergyCompareProjection> rows
	) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		return rows.stream()
				.filter(row -> row.getScaleHour() != null)
				.map(row -> new HourlyCompareDto(
						row.getScaleHour(),
						row.getYesterday(),
						row.getToday(),
						row.getYesterdayUsd(),
						row.getTodayUsd()
				))
				.toList();
	}

	// ============================================================
	// NORMALIZATION
	// ============================================================

	private String normalizeFac(String facId) {
		if (facId == null || facId.isBlank()) {
			return DEFAULT_FAC;
		}

		final String normalized = facId.trim();

		if (!ALLOWED_FACS.contains(normalized)) {
			throw new IllegalArgumentException(
					"Invalid facId: " + normalized
			);
		}

		return normalized;
	}

	private String normalizeMetric(String nameEn) {
		if (nameEn == null || nameEn.isBlank()) {
			return DEFAULT_METRIC;
		}

		return nameEn.trim();
	}

	private String normalizeSensorType(String type) {
		if (type == null || type.isBlank()) {
			return "WATER";
		}

		final String normalized =
				type.trim().toUpperCase(Locale.ROOT);

		if (!normalized.equals("WATER")
				&& !normalized.equals("AIR")) {
			throw new IllegalArgumentException(
					"type must be WATER or AIR"
			);
		}

		return normalized;
	}

	private BigDecimal normalizeExchange(
			BigDecimal exchange
	) {
		if (exchange == null
				|| exchange.compareTo(BigDecimal.ZERO) <= 0) {
			return DEFAULT_EXCHANGE;
		}

		return exchange;
	}

	private BigDecimal normalizeSepzone(
			BigDecimal sepzone
	) {
		if (sepzone == null
				|| sepzone.compareTo(BigDecimal.ZERO) <= 0) {
			return DEFAULT_SEPZONE;
		}

		return sepzone;
	}
}