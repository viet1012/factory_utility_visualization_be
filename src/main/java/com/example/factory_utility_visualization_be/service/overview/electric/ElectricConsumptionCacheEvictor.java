package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Clears response caches after EVN rows or a loss calibration were written,
 * so the new evn / official / calibrated values show up (closed periods are
 * cached for long otherwise). Inside a transaction the clear runs after commit.
 *
 * - evictAfterCommit (loss calibration): GET /electric-consumption
 *   (current + closed bill periods)
 * - evictEvnAfterCommit (EVN import): the same + the dashboards with official
 *   numbers: monthly-summary (MONTHLY_CURRENT / MONTHLY_HISTORY). energy-daily
 *   and solar/monthly have no response cache (computed per request from
 *   HourlyEnergyCache), so the new EVN rows show up at once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElectricConsumptionCacheEvictor {

	private static final String[] ELECTRIC_CONSUMPTION = {
			UtilityCacheNames.ELECTRIC_CONSUMPTION_CURRENT,
			UtilityCacheNames.ELECTRIC_CONSUMPTION_CLOSED};

	// dashboards that show official (EVN) numbers
	private static final String[] DASHBOARDS = {
			UtilityCacheNames.MONTHLY_CURRENT,
			UtilityCacheNames.MONTHLY_HISTORY};

	private final CacheManager cacheManager;
	// running bill period (versioned cache, not a Spring cache)
	private final ElectricConsumptionCacheService cacheService;

	// loss calibration: only /electric-consumption shows calibrated values
	public void evictAfterCommit(String reason) {
		afterCommit(() -> evict(reason, false));
	}

	// EVN import: /electric-consumption + dashboards
	public void evictEvnAfterCommit(String reason) {
		afterCommit(() -> evict(reason, true));
	}

	private void afterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		} else {
			action.run();
		}
	}

	private void evict(String reason, boolean dashboards) {
		clear(ELECTRIC_CONSUMPTION);
		cacheService.clearCurrent();

		if (dashboards) {
			clear(DASHBOARDS);
		}

		log.info("electric-consumption{} caches cleared: {}", dashboards ? " + monthly-summary" : "", reason);
	}

	private void clear(String[] names) {
		for (String name : names) {
			Cache cache = cacheManager.getCache(name);

			if (cache != null) {
				cache.clear();
			}
		}
	}
}
