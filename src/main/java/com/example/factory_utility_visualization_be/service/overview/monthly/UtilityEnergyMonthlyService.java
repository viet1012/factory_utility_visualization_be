package com.example.factory_utility_visualization_be.service.overview.monthly;


import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.repository.monthly.UtilityMonthlyRepo;
import lombok.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityEnergyMonthlyService {

	private final UtilityMonthlyRepo repo;

	public List<MonthlySummaryDto> getMonthlySummary(
			String facId,
			String monthYyyyMm,
			List<String> names
	) {

		int year = Integer.parseInt(monthYyyyMm.substring(0, 4));
		int month = Integer.parseInt(monthYyyyMm.substring(4, 6));

		LocalDate firstDay = LocalDate.of(year, month, 1);
		LocalDateTime from = firstDay.atStartOfDay();
		LocalDateTime to = firstDay.plusMonths(1).atStartOfDay();

		List<MonthlySummaryProjection> results =
				repo.sumMonthlyByNamesRaw(facId, monthYyyyMm, names, from, to);

		return results.stream()
				.map(r -> new MonthlySummaryDto(
						r.getCate(),
						r.getName(),
						r.getMonth(),
						r.getValue(),
						r.getUnit()
				))
				.toList();
	}
}