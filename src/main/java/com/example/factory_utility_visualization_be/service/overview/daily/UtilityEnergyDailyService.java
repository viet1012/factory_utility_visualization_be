package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.DailyElectricityStackDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardDto;
import com.example.factory_utility_visualization_be.repository.overview.daily.projection.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import com.example.factory_utility_visualization_be.service.overview.electric.DashboardEnergyService;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit;
import lombok.extern.slf4j.Slf4j;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import com.example.factory_utility_visualization_be.service.util.YearMonthParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityEnergyDailyService {

	private static final DateTimeFormatter MONTH_FORMATTER =
			DateTimeFormatter.ofPattern("yyyyMM");

	private final UtilityDailyRepo repo;
	private final DashboardEnergyService dashboardEnergy;

	@Transactional(readOnly = true)
	public UtilityDailyDashboardDto getDailyDashboard(
			String facId,
			String monthYyyyMm
	) {

		// =====================================================
		// 1. NORMALIZE INPUT
		// =====================================================

		final String fac =
				normalizeRequired(
						facId,
						"facId"
				);

		final YearMonth yearMonth =
				parseMonth(monthYyyyMm);

		// =====================================================
		// 2. DATE RANGE
		// =====================================================

		final LocalDateTime from =
				yearMonth
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime to =
				yearMonth
						.plusMonths(1)
						.atDay(1)
						.atStartOfDay();

		// =====================================================
		// 3. ELECTRICITY (GridSolarSplit, same as /electric-consumption)
		//
		// per hour: load = Σ sub-meter, grid = max(load - solar, 0)
		// gridKwh  = Σ grid of the day (bar "Electricity")
		// solarKwh = Σ solar of the day (stacked on top)
		// totalKwh = Σ load of the day (= grid + solar, except hours
		//            where solar > load)
		// =====================================================

		final long started = System.nanoTime();

		final List<DailyElectricityStackDto> electricity =
				new ArrayList<>();

		GridSolarSplit.byDay(dashboardEnergy.facHours(fac, from, to))
				.forEach((day, t) -> {
					if (t.hasData()) {
						electricity.add(
								new DailyElectricityStackDto(
										day,
										oneDecimal(t.grid()),
										oneDecimal(t.solar()),
										oneDecimal(t.load())
								)
						);
					}
				});

		// =====================================================
		// 4. WATER + AIR
		// =====================================================

		final long electricityNs = System.nanoTime();

		final List<UtilityDailyDashboardProjection> utilityRows =
				repo.getDailyWaterAndAirByDateRange(
						fac,
						from,
						to
				);

		log.info("energy-daily {} {}: electricity (hourly energy) {} ms, water/air SQL {} ms",
				fac, monthYyyyMm,
				(electricityNs - started) / 1_000_000,
				(System.nanoTime() - electricityNs) / 1_000_000);

		final List<DailyDto> water =
				new ArrayList<>();

		final List<DailyDto> air =
				new ArrayList<>();

		if (utilityRows != null) {

			for (UtilityDailyDashboardProjection row : utilityRows) {

				if (
						row == null
								|| row.getRecordDate() == null
				) {
					continue;
				}

				final String type =
						normalizeUtilityType(
								row.getUtilityType()
						);

				// Tất cả Water/Air đều làm tròn 1 chữ số.
				final BigDecimal value =
						oneDecimal(
								row.getValue()
						);

				switch (type) {

					// =================================================
					// WATER
					// =================================================

					case "WATER" -> water.add(
							new DailyDto(
									row.getRecordDate()
											.toLocalDate(),

									value,

									// Tạm thời không dùng cost
									null
							)
					);

					// =================================================
					// AIR
					// =================================================

					case "AIR" -> air.add(
							new DailyDto(
									row.getRecordDate()
											.toLocalDate(),

									value,

									// Tạm thời không dùng cost
									null
							)
					);

					// =================================================
					// ENERGY
					//
					// Không lấy tại đây.
					// Electricity đã có query riêng:
					// getDailyElectricityStack()
					// =================================================

					case "ENERGY" -> {
						// Ignore.
					}

					default -> {
						// Ignore unknown utility type.
					}
				}
			}
		}

		// =====================================================
		// 5. RESPONSE
		// =====================================================

		return UtilityDailyDashboardDto
				.builder()

				.facId(
						fac
				)

				.month(
						yearMonth.format(
								MONTH_FORMATTER
						)
				)

				.electricity(
						List.copyOf(
								electricity
						)
				)

				.water(
						List.copyOf(
								water
						)
				)

				.air(
						List.copyOf(
								air
						)
				)

				.build();
	}

	// =========================================================
	// ROUND 1 DECIMAL
	//
	// 123.456 -> 123.5
	// 123.44  -> 123.4
	// null    -> 0.0
	// =========================================================

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

	// =========================================================
	// NORMALIZE UTILITY TYPE
	// =========================================================

	private String normalizeUtilityType(
			String value
	) {
		if (
				value == null
						|| value.isBlank()
		) {
			return "";
		}

		return value
				.trim()
				.toUpperCase();
	}

	// =========================================================
	// PARSE MONTH
	//
	// yyyyMM
	//
	// Example:
	// 202608
	// =========================================================

	private YearMonth parseMonth(
			String value
	) {
		return YearMonthParser.parse(
				value,
				"month must be yyyyMM, for example 202608",
				"Invalid month: "
		);
	}

	// =========================================================
	// REQUIRED STRING
	// =========================================================

	private String normalizeRequired(
			String value,
			String fieldName
	) {
		return FacilityValidator.normalizeRequired(value, fieldName);
	}
}