package com.example.factory_utility_visualization_be.dto.overview.monthly;


import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityMonthlyCacheService {

	private final UtilityMonthlyQueryService queryService;

	/**
	 * Tháng hiện tại:
	 * cache trong 10 phút.
	 *
	 * sync = true:
	 * nhiều request cùng key chỉ chạy một query SQL.
	 */
	@Cacheable(
			cacheNames = UtilityCacheNames.MONTHLY_CURRENT,
			key = "#fac + '_' + #monthYyyyMm",
			sync = true
	)
	public List<MonthlySummaryDto> getCurrentMonth(
			String fac,
			String monthYyyyMm,
			MonthlyQueryRange range
	) {
		return queryService.query(
				fac,
				monthYyyyMm,
				range
		);
	}

	/**
	 * Tháng quá khứ:
	 * cache trong 12 giờ.
	 */
	@Cacheable(
			cacheNames = UtilityCacheNames.MONTHLY_HISTORY,
			key = "#fac + '_' + #monthYyyyMm",
			sync = true
	)
	public List<MonthlySummaryDto> getHistoryMonth(
			String fac,
			String monthYyyyMm,
			MonthlyQueryRange range
	) {
		return queryService.query(
				fac,
				monthYyyyMm,
				range
		);
	}

	@CacheEvict(
			cacheNames = UtilityCacheNames.MONTHLY_CURRENT,
			key = "#fac + '_' + #monthYyyyMm"
	)
	public void evictCurrentMonth(
			String fac,
			String monthYyyyMm
	) {
	}

	@CacheEvict(
			cacheNames = UtilityCacheNames.MONTHLY_HISTORY,
			key = "#fac + '_' + #monthYyyyMm"
	)
	public void evictHistoryMonth(
			String fac,
			String monthYyyyMm
	) {
	}

	@CacheEvict(
			cacheNames = {
					UtilityCacheNames.MONTHLY_CURRENT,
					UtilityCacheNames.MONTHLY_HISTORY
			},
			allEntries = true
	)
	public void clearAll() {
	}
}