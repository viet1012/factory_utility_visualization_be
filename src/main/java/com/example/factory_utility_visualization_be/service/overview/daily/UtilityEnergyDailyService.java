package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyEnergyCostProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		final String normalizedFac = normalizeRequired(
				facId,
				"facId"
		);

		final YearMonth yearMonth = parseMonth(monthYyyyMm);

		final LocalDateTime from =
				yearMonth.atDay(1).atStartOfDay();

		final LocalDateTime to =
				yearMonth.plusMonths(1)
						.atDay(1)
						.atStartOfDay();

		/*
		 * Dữ liệu điện, nước, khí hiện tại.
		 */
		List<UtilityDailyDashboardProjection> rows =
				repo.getDailyDashboardByMonth(
						normalizedFac,
						from,
						to
				);

		/*
		 * Dữ liệu điện năng và chi phí từ bảng hourly.
		 */
		List<UtilityDailyEnergyCostProjection> energyCostRows =
				repo.getDailyEnergyAndCost(
						normalizedFac,
						from,
						to
				);

		/*
		 * Map chi phí theo ngày.
		 */
		Map<LocalDate, BigDecimal> costUsdByDate =
				new LinkedHashMap<>();

		/*
		 * Map kWh theo ngày từ bảng hourly.
		 * Nên dùng nguồn này cho biểu đồ điện để kWh và tiền
		 * cùng lấy từ một bảng, tránh lệch dữ liệu.
		 */
		Map<LocalDate, BigDecimal> energyKwhByDate =
				new LinkedHashMap<>();

		for (UtilityDailyEnergyCostProjection row : energyCostRows) {
			if (row.getRecordDate() == null) {
				continue;
			}

			energyKwhByDate.put(
					row.getRecordDate(),
					defaultZero(row.getEnergyKwh())
			);

			costUsdByDate.put(
					row.getRecordDate(),
					defaultZero(row.getCostUsd())
			);
		}

		List<DailyDto> electricity = new ArrayList<>();
		List<DailyDto> water = new ArrayList<>();
		List<DailyDto> air = new ArrayList<>();

		/*
		 * Điện lấy trực tiếp từ bảng F2_Utility_Energy_Hourly.
		 * Như vậy mỗi ngày có cả kWh và CostUsd.
		 */
		for (UtilityDailyEnergyCostProjection row : energyCostRows) {
			if (row.getRecordDate() == null) {
				continue;
			}

			electricity.add(
					new DailyDto(
							row.getRecordDate(),
							defaultZero(row.getEnergyKwh()),
							defaultZero(row.getCostUsd())
					)
			);
		}

		/*
		 * Query dashboard cũ tiếp tục dùng cho Water và Air.
		 * Không lấy ENERGY ở đây nữa để tránh dữ liệu điện bị trùng.
		 */
		for (UtilityDailyDashboardProjection row : rows) {
			if (row.getRecordDate() == null) {
				continue;
			}

			final LocalDate date =
					row.getRecordDate().toLocalDate();

			final BigDecimal value =
					defaultZero(row.getValue());

			final String type =
					row.getUtilityType() == null
							? ""
							: row.getUtilityType()
							.trim()
							.toUpperCase();

			switch (type) {
				case "WATER" -> water.add(
						new DailyDto(
								date,
								value,
								null
						)
				);

				case "AIR" -> air.add(
						new DailyDto(
								date,
								value,
								null
						)
				);

				case "ENERGY" -> {
					/*
					 * Bỏ qua vì electricity đã lấy từ
					 * F2_Utility_Energy_Hourly.
					 */
				}

				default -> {
					// Ignore unknown utility type.
				}
			}
		}

		return UtilityDailyDashboardDto.builder()
				.facId(normalizedFac)
				.month(yearMonth.format(MONTH_FORMATTER))
				.electricity(List.copyOf(electricity))
				.water(List.copyOf(water))
				.air(List.copyOf(air))
				.build();
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null
				? BigDecimal.ZERO
				: value;
	}

	private YearMonth parseMonth(String value) {
		if (value == null || !value.matches("\\d{6}")) {
			throw new IllegalArgumentException(
					"month must be yyyyMM, for example 202607"
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

	private String normalizeRequired(
			String value,
			String fieldName
	) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " is required"
			);
		}

		return normalizeFac(value);
	}

	private String normalizeFac(String value) {
		String fac = value.trim();

		if (fac.equalsIgnoreCase("KVH")) {
			return "KVH";
		}

		if (fac.equalsIgnoreCase("FAC_A")) {
			return "Fac_A";
		}

		if (fac.equalsIgnoreCase("FAC_B")) {
			return "Fac_B";
		}

		if (fac.equalsIgnoreCase("FAC_C")) {
			return "Fac_C";
		}

		return fac;
	}
}