package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * Caches for GET /api/utility/electric-consumption, keyed by month + months.
 * Which one is used is decided by ElectricConsumptionService (separate bean
 * so the proxy applies).
 */
@Service
@RequiredArgsConstructor
public class ElectricConsumptionCacheService {

	private final ElectricConsumptionCalculator calculator;

	// bill period still running
	@Cacheable(
			cacheNames = UtilityCacheNames.ELECTRIC_CONSUMPTION_CURRENT,
			key = "#month.toString() + '_' + #months + '_' + #hourOffset",
			sync = true
	)
	public ElectricConsumptionResponseDto getCurrent(YearMonth month, int months, int hourOffset) {
		return calculator.calculate(month, months, hourOffset);
	}

	// bill period already ended: cannot change any more
	@Cacheable(
			cacheNames = UtilityCacheNames.ELECTRIC_CONSUMPTION_CLOSED,
			key = "#month.toString() + '_' + #months + '_' + #hourOffset",
			sync = true
	)
	public ElectricConsumptionResponseDto getClosed(YearMonth month, int months, int hourOffset) {
		return calculator.calculate(month, months, hourOffset);
	}
}
