package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import lombok.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

		List<UtilityDailyDashboardProjection> rows =
				repo.getDailyDashboardByMonth(
						normalizedFac,
						from,
						to
				);

		List<DailyDto> electricity = new ArrayList<>();
		List<DailyDto> water = new ArrayList<>();
		List<DailyDto> air = new ArrayList<>();

		for (UtilityDailyDashboardProjection row : rows) {
			if (row.getRecordDate() == null) {
				continue;
			}

			final LocalDate date =
					row.getRecordDate().toLocalDate();

			final BigDecimal value =
					row.getValue() == null
							? BigDecimal.ZERO
							: row.getValue();

			final DailyDto dto =
					new DailyDto(date, value);

			final String type =
					row.getUtilityType() == null
							? ""
							: row.getUtilityType().trim().toUpperCase();

			switch (type) {
				case "ENERGY" -> electricity.add(dto);
				case "WATER" -> water.add(dto);
				case "AIR" -> air.add(dto);
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

		return value.trim();
	}
}