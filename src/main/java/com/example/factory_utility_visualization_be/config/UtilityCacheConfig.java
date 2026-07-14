package com.example.factory_utility_visualization_be.config;


import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class UtilityCacheConfig {

	@Bean
	public CacheManager cacheManager() {
		final CaffeineCache currentMonthCache =
				new CaffeineCache(
						UtilityCacheNames.MONTHLY_CURRENT,
						Caffeine.newBuilder()
								.maximumSize(100)
								.expireAfterWrite(
										Duration.ofMinutes(10)
								)
								.recordStats()
								.build()
				);

		final CaffeineCache historyMonthCache =
				new CaffeineCache(
						UtilityCacheNames.MONTHLY_HISTORY,
						Caffeine.newBuilder()
								.maximumSize(300)
								.expireAfterWrite(
										Duration.ofHours(12)
								)
								.recordStats()
								.build()
				);

		final SimpleCacheManager cacheManager =
				new SimpleCacheManager();

		cacheManager.setCaches(
				List.of(
						currentMonthCache,
						historyMonthCache
				)
		);

		return cacheManager;
	}
}