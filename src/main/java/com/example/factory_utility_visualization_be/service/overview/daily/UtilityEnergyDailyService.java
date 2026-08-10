package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.DailyElectricityStackDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyElectricityStackProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityEnergyDailyService {

	private static final DateTimeFormatter MONTH_FORMATTER =
			DateTimeFormatter.ofPattern("yyyyMM");

	private final UtilityDailyRepo repo;

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
		// 3. ELECTRICITY
		//
		// API trả:
		// gridKwh
		// solarKwh
		// totalKwh
		//
		// Dùng cho stacked column chart.
		// =====================================================

		final List<UtilityDailyElectricityStackProjection> energyRows =
				repo.getDailyElectricityStack(
						fac,
						from,
						to
				);

		final List<DailyElectricityStackDto> electricity =
				new ArrayList<>();

		if (energyRows != null) {

			for (UtilityDailyElectricityStackProjection row : energyRows) {

				if (
						row == null
								|| row.getRecordDate() == null
				) {
					continue;
				}

				electricity.add(
						new DailyElectricityStackDto(
								row.getRecordDate(),

								// Grid electricity
								oneDecimal(
										row.getGridKwh()
								),

								// Solar
								oneDecimal(
										row.getSolarKwh()
								),

								// Total = Grid + Solar
								oneDecimal(
										row.getTotalKwh()
								)
						)
				);
			}
		}

		// =====================================================
		// 4. WATER + AIR
		// =====================================================

		final List<UtilityDailyDashboardProjection> utilityRows =
				repo.getDailyDashboardByMonth(
						fac,
						from,
						to
				);

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

		if (
				value == null
						|| !value.matches("\\d{6}")
		) {
			throw new IllegalArgumentException(
					"month must be yyyyMM, for example 202608"
			);
		}

		try {

			return YearMonth.parse(
					value,
					MONTH_FORMATTER
			);

		} catch (DateTimeException e) {

			throw new IllegalArgumentException(
					"Invalid month: " + value,
					e
			);
		}
	}

	// =========================================================
	// REQUIRED STRING
	// =========================================================

	private String normalizeRequired(
			String value,
			String fieldName
	) {

		if (
				value == null
						|| value.isBlank()
		) {
			throw new IllegalArgumentException(
					fieldName + " is required"
			);
		}

		return normalizeFac(
				value
		);
	}

	// =========================================================
	// NORMALIZE FAC
	// =========================================================

	private String normalizeFac(
			String value
	) {

		final String fac =
				value.trim();

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