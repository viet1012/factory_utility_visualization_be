package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Caches for GET /api/utility/power-usage, keyed by period + date. Which one
 * is used is decided by PowerUsageService (separate bean so the proxy
 * applies).
 */
@Service
@RequiredArgsConstructor
public class PowerUsageCacheService {

	private final PowerUsageCalculator calculator;

	// period still running: 60s
	@Cacheable(
			cacheNames = UtilityCacheNames.POWER_USAGE_CURRENT,
			key = "#period.name() + '_' + #date",
			sync = true
	)
	public PowerUsageResponseDto getCurrent(PowerUsagePeriod period, LocalDate date) {
		return calculator.calculate(period, date);
	}

	// period already ended: cannot change any more, long TTL
	@Cacheable(
			cacheNames = UtilityCacheNames.POWER_USAGE_CLOSED,
			key = "#period.name() + '_' + #date",
			sync = true
	)
	public PowerUsageResponseDto getClosed(PowerUsagePeriod period, LocalDate date) {
		return calculator.calculate(period, date);
	}
}
