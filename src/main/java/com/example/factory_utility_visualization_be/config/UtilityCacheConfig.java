package com.example.factory_utility_visualization_be.config;


import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableCaching
public class UtilityCacheConfig {

	@Bean
	public CacheManager cacheManager() {
		final Cache currentMonthCache =
				new CaffeineCache(
						UtilityCacheNames.MONTHLY_CURRENT,
						buildNativeCache(100, Duration.ofMinutes(10))
				);

		final Cache historyMonthCache =
				new CaffeineCache(
						UtilityCacheNames.MONTHLY_HISTORY,
						buildNativeCache(300, Duration.ofHours(12))
				);

		final Cache scadaMasterCache =
				new CaffeineCache(
						UtilityCacheNames.SCADA_MASTER,
						buildNativeCache(10, Duration.ofSeconds(60))
				);

		final Cache channelMasterCache =
				new CaffeineCache(
						UtilityCacheNames.CHANNEL_MASTER,
						buildNativeCache(10, Duration.ofSeconds(60))
				);

		// Historical months only (current month always bypasses this cache
		// — see SolarDashboardService/SolarDetailService), so a completed
		// month's result cannot change; long TTL matches MONTHLY_HISTORY.
		final Cache solarMonthlySummaryCache =
				new CaffeineCache(
						UtilityCacheNames.SOLAR_MONTHLY_SUMMARY,
						buildNativeCache(300, Duration.ofHours(12))
				);

		final SimpleCacheManager cacheManager =
				new SimpleCacheManager();

		cacheManager.setCaches(
				List.of(
						currentMonthCache,
						historyMonthCache,
						scadaMasterCache,
						channelMasterCache,
						solarMonthlySummaryCache
				)
		);

		return cacheManager;
	}

	@NonNull
	private static com.github.benmanes.caffeine.cache.Cache<Object, Object> buildNativeCache(
			long maximumSize,
			Duration expiration
	) {
		return Objects.requireNonNull(
				Caffeine.newBuilder()
						.maximumSize(maximumSize)
						.expireAfterWrite(expiration)
						.recordStats()
						.build(),
				"Caffeine cache builder returned null"
		);
	}
}
