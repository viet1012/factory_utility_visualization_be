package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityEnergyMonthlyService {

	private final UtilityMonthlyRepo repo;

	private static final BigDecimal DEFAULT_EXCHANGE = new BigDecimal("25585");
	private static final BigDecimal DEFAULT_SEPZONE = BigDecimal.ONE;

	public List<MonthlySummaryDto> getMonthlySummary(
			String facId,
			String monthYyyyMm
	) {
		int year = Integer.parseInt(monthYyyyMm.substring(0, 4));
		int month = Integer.parseInt(monthYyyyMm.substring(4, 6));

		LocalDate firstDay = LocalDate.of(year, month, 1);

		LocalDateTime from = firstDay.atStartOfDay();
		LocalDateTime monthEnd = firstDay.plusMonths(1).atStartOfDay();

		LocalDateTime now = LocalDateTime.now();

		LocalDateTime currentTo;

		if (now.isBefore(from)) {
			currentTo = from;
		} else if (now.isAfter(monthEnd)) {
			currentTo = monthEnd;
		} else {
			currentTo = now;
		}

		LocalDateTime prevFrom = firstDay.minusMonths(1).atStartOfDay();

		Duration elapsed = Duration.between(from, currentTo);
		LocalDateTime prevTo = prevFrom.plus(elapsed);

		List<MonthlySummaryProjection> results =
				repo.sumMonthlyByNamesRaw(
						facId,
						monthYyyyMm,
						from,
						currentTo,
						prevFrom,
						prevTo,
						DEFAULT_EXCHANGE,
						DEFAULT_SEPZONE
				);

		return results.stream()
				.map(r -> new MonthlySummaryDto(
						r.getCate(),
						r.getName(),
						r.getMonth(),

						r.getMinValue(),
						r.getMaxValue(),
						r.getPrevMinValue(),
						r.getPrevMaxValue(),

						r.getValue(),
						r.getAvgValue(),

						r.getVndCost(),
						r.getUsdCost(),

						r.getPrevValue(),
						r.getPrevAvgValue(),

						r.getPrevVndCost(),
						r.getPrevUsdCost(),

						r.getDeltaValue(),
						r.getDeltaPercent(),

						r.getUnit(),
						r.getPickAt(),
						OffsetDateTime.now()
				))
				.toList();
	}
}