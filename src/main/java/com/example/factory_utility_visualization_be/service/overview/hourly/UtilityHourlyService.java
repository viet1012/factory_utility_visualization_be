package com.example.factory_utility_visualization_be.service.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.*;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlyEnergyProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlySensorCompareProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Money;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class UtilityHourlyService {

	private static final String DEFAULT_METRIC =
			"Total Energy Consumption";

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	// kWh / USD scale of the old SQL (DECIMAL(19,4))
	private static final int MONEY_SCALE = 4;

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
		// Không bao gồm Solar. SQL chỉ trả kWh theo ngày x giờ; tiền
		// tính ở đây bằng ElectricityPricingService (nơi duy nhất tính
		// tiền điện). Dòng History_Main ghi điện của giờ KẾT THÚC tại
		// pick_at -> cửa sổ query dịch +1h.
		// ========================================================

		final List<HourlyEnergyProjection>
				electricityRows =
				repo.findHourlyElectricity(
						fac,
						fromTime.plusHours(1),
						toTime.plusHours(1),
						metric,
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

		final EnergyCompare electricity =
				mapEnergy(electricityRows, yesterday, today);


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

				List.copyOf(electricity.rows()),

				List.copyOf(sensorSplit.water()),
				List.copyOf(sensorSplit.air()),

				electricity.priceMissing()
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

	private record EnergyCompare(
			List<HourlyCompareDto> rows,
			// some hour with kWh had no tariff version (its USD is null)
			boolean priceMissing
	) {
	}

	// kWh + USD of one day-hour; usd null = no price for that hour
	private record HourValue(BigDecimal kwh, BigDecimal usd) {
	}

	/*
	 * Một dòng cho mỗi giờ có dữ liệu ở hôm qua hoặc hôm nay (như SQL cũ).
	 * kWh thiếu = 0. USD = kWh x giá của version hiệu lực vào ngày đó
	 * (ElectricityPricingService); ngày không có version / không có tariff
	 * -> USD = null (không phải 0) và priceMissing = true nếu giờ đó có kWh.
	 */
	private EnergyCompare mapEnergy(
			List<HourlyEnergyProjection> rows,
			LocalDate yesterday,
			LocalDate today
	) {

		if (rows == null || rows.isEmpty()) {
			return new EnergyCompare(List.of(), false);
		}

		final Optional<Pricing> pricing =
				pricingService.load();

		// hour -> day -> value
		final Map<Integer, Map<LocalDate, HourValue>> byHour =
				new TreeMap<>();

		boolean priceMissing = false;

		for (HourlyEnergyProjection row : rows) {

			if (row == null
					|| row.getHourNumber() == null
					|| row.getRecordDate() == null) {
				continue;
			}

			final LocalDate day =
					row.getRecordDate().toLocalDate();

			if (!day.equals(yesterday) && !day.equals(today)) {
				continue;
			}

			final BigDecimal kwh =
					zeroIfNull(row.getKwh());

			final LocalDateTime hourAt =
					day.atTime(row.getHourNumber(), 0);

			final BigDecimal usd = pricing
					.flatMap(p -> p.gridCost(hourAt, kwh))
					.map(Money::usd)
					.orElse(null);

			if (usd == null && kwh.signum() != 0) {
				priceMissing = true;
			}

			byHour
					.computeIfAbsent(row.getHourNumber(), key -> new HashMap<>())
					.put(day, new HourValue(kwh, usd));
		}

		final List<HourlyCompareDto> result =
				new ArrayList<>(byHour.size());

		byHour.forEach((hour, days) -> result.add(
				new HourlyCompareDto(
						hour,
						kwh(days.get(yesterday)),
						kwh(days.get(today)),
						usd(days.get(yesterday), pricing, yesterday),
						usd(days.get(today), pricing, today)
				)
		));

		return new EnergyCompare(result, priceMissing);
	}

	private BigDecimal kwh(HourValue value) {
		return value == null
				? BigDecimal.ZERO.setScale(MONEY_SCALE)
				: value.kwh().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	// no kWh row for that day-hour -> 0 when the day is priced, else null
	private BigDecimal usd(HourValue value, Optional<Pricing> pricing, LocalDate day) {
		if (value == null) {
			return pricing.isPresent() && pricing.get().priced(day)
					? BigDecimal.ZERO.setScale(MONEY_SCALE)
					: null;
		}

		return value.usd() == null
				? null
				: value.usd().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
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