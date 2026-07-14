package com.example.factory_utility_visualization_be.dto.overview.monthly;


import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityMonthlyQueryService {

	private static final BigDecimal DEFAULT_EXCHANGE =
			new BigDecimal("25585");

	private static final BigDecimal DEFAULT_SEPZONE =
			BigDecimal.ONE;

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private final UtilityMonthlyRepo repo;

	@Transactional(readOnly = true)
	public List<MonthlySummaryDto> query(
			String fac,
			String monthYyyyMm,
			MonthlyQueryRange range
	) {
		log.info(
				"Query monthly DB: fac={}, month={}, from={}, currentTo={}, prevFrom={}, prevTo={}",
				fac,
				monthYyyyMm,
				range.from(),
				range.currentTo(),
				range.prevFrom(),
				range.prevTo()
		);

		final List<MonthlySummaryProjection> projections;

		if ("KVH".equalsIgnoreCase(fac)) {
			projections = repo.sumMonthlyKvhRaw(
					monthYyyyMm,
					range.from(),
					range.currentTo(),
					range.prevFrom(),
					range.prevTo(),
					DEFAULT_EXCHANGE,
					DEFAULT_SEPZONE
			);
		} else {
			projections = repo.sumMonthlyByFacRaw(
					fac,
					monthYyyyMm,
					range.from(),
					range.currentTo(),
					range.prevFrom(),
					range.prevTo(),
					DEFAULT_EXCHANGE,
					DEFAULT_SEPZONE
			);
		}

		if (projections == null || projections.isEmpty()) {
			return List.of();
		}

		final OffsetDateTime generatedAt =
				OffsetDateTime.now(APP_ZONE);

		final List<MonthlySummaryDto> result =
				new ArrayList<>(projections.size());

		for (MonthlySummaryProjection row : projections) {
			if (row == null) {
				continue;
			}

			result.add(
					mapToDto(
							row,
							generatedAt
					)
			);
		}

		return List.copyOf(result);
	}

	private MonthlySummaryDto mapToDto(
			MonthlySummaryProjection row,
			OffsetDateTime generatedAt
	) {
		return new MonthlySummaryDto(
				row.getCate(),
				row.getName(),
				row.getMonth(),

				row.getMinValue(),
				row.getMaxValue(),
				row.getPrevMinValue(),
				row.getPrevMaxValue(),

				row.getValue(),
				row.getAvgValue(),

				row.getVndCost(),
				row.getUsdCost(),

				row.getPrevValue(),
				row.getPrevAvgValue(),

				row.getPrevVndCost(),
				row.getPrevUsdCost(),

				row.getDeltaValue(),
				row.getDeltaPercent(),

				row.getUnit(),
				row.getPickAt(),
				generatedAt
		);
	}
}