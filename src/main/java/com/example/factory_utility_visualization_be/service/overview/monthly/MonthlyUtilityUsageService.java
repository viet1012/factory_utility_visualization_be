package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyUtilityUsageDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.MonthlyUtilityUsageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonthlyUtilityUsageService {

	private final MonthlyUtilityUsageRepo repo;

	public List<MonthlyUtilityUsageDto> getMonthlyUsage(
			String fac,
			int year,
			int month,
			String nameEn
	) {
		LocalDate from = LocalDate.of(year, month, 1);
		LocalDate to = from.plusMonths(1);

		return repo.findMonthlyUtilityUsageDto(
				fac,
				nameEn,
				from.atStartOfDay(),
				to.atStartOfDay()
		);
	}
}