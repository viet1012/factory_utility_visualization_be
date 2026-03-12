package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import lombok.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityEnergyDailyService {
	private final UtilityDailyRepo repo;

	public List<DailyDto> getDaily(String facId, String monthYyyyMm, String nameEn) {
		if (monthYyyyMm == null || !monthYyyyMm.matches("\\d{6}")) {
			throw new IllegalArgumentException("month must be yyyyMM (e.g. 202612)");
		}

		int year = Integer.parseInt(monthYyyyMm.substring(0, 4));
		int month = Integer.parseInt(monthYyyyMm.substring(4, 6));

		LocalDate firstDay = LocalDate.of(year, month, 1);
		LocalDate firstDayNext = firstDay.plusMonths(1);

		LocalDateTime from = firstDay.atStartOfDay();
		LocalDateTime to = firstDayNext.atStartOfDay();
		final String metric = (nameEn == null || nameEn.isBlank())
				? "Total Energy Consumption"
				: nameEn.trim();
		List<Object[]> rows = repo.sumDailyEnergyByMonth(facId, from, to, metric);

		return rows.stream().map(r -> {
			LocalDate date = ((java.sql.Date) r[0]).toLocalDate();
			BigDecimal value = (r[1] == null) ? BigDecimal.ZERO : new BigDecimal(r[1].toString());
			return new DailyDto(date, value);
		}).toList();
	}
}