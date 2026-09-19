package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarDashboardProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Cache for the solar monthly summary row of a COMPLETED month only.
 *
 * Never call this for the current month: solarKwh/gridKwh/totalKwh/
 * solarSharePercent accumulate throughout the current month (MTD rows) and
 * currentPowerKw is a live gauge reading, so caching it would make the FE see
 * stale "current power"/live share values across polls. Callers must route
 * current-month requests straight to SolarDashboardRepo instead — see
 * SolarDashboardService/SolarDetailService.
 *
 * A completed month's query window is closed and cannot change afterward, so
 * this cache uses a long TTL (see UtilityCacheConfig), consistent with
 * UtilityMonthlyCacheService's MONTHLY_HISTORY convention.
 */
@Service
@RequiredArgsConstructor
public class SolarMonthlySummaryCacheService {

	private final SolarDashboardRepo repo;

	@Cacheable(
			cacheNames = UtilityCacheNames.SOLAR_MONTHLY_SUMMARY,
			key = "#fac + '_' + #monthKey",
			sync = true
	)
	public SolarDashboardProjection getHistoricalSummary(
			String fac,
			String monthKey,
			LocalDateTime monthStart,
			LocalDateTime nextMonthStart,
			LocalDateTime now,
			String powerName,
			String energyName
	) {
		return repo.getSolarDashboardByMonth(
				fac,
				monthStart,
				nextMonthStart,
				now,
				powerName,
				energyName
		);
	}
}
