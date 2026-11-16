package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyQueryRange;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.EnvAgg;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Water / Air of monthly-summary for a CLOSED month (window [month start,
 * month end) + previous month, fixed), computed ONCE for every fac (KVH,
 * FAC_A/B/C) instead of once per fac, and cached per month - same freshness
 * rules as HourlyEnergyCache:
 * - loaded after the month closed (+ grace): kept CLOSED_MAX_AGE
 * - loaded right after the month end (late rows may still arrive): RECENT_MAX_AGE
 *
 * Single-flight: concurrent requests for a month that is loading wait for the
 * same future; a failed load is dropped by Caffeine and retried next time.
 * The running month does not come here (its window ends at "now"):
 * UtilityMonthlyQueryService queries it per fac.
 */
@Slf4j
@Service
public class MonthlyEnvCache {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final Duration CLOSED_MAX_AGE = Duration.ofHours(12);
	private static final Duration RECENT_MAX_AGE = Duration.ofMinutes(10);
	private static final Duration CLOSE_GRACE = Duration.ofHours(2);

	private static final int MAX_MONTHS = 24;

	private record Entry(List<EnvAgg> rows, LocalDateTime loadedAt, boolean closedAtLoad) {}

	private final UtilityMonthlyJdbcRepository repository;

	private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "monthly-env-loader");
		thread.setDaemon(true);
		return thread;
	});

	private final AsyncCache<YearMonth, Entry> cache = Caffeine.newBuilder()
			.maximumSize(MAX_MONTHS)
			.buildAsync();

	public MonthlyEnvCache(UtilityMonthlyJdbcRepository repository) {
		this.repository = repository;
	}

	@PreDestroy
	void shutdown() {
		loader.shutdownNow();
	}

	/**
	 * Every fac's water / air aggregates of the closed month `month`; range =
	 * its monthly-summary range (currentTo = month end, prevTo = start of day).
	 */
	public List<EnvAgg> closedMonth(YearMonth month, MonthlyQueryRange range) {
		return cache.asMap()
				.compute(month, (key, old) -> usable(old)
						? old
						: CompletableFuture.supplyAsync(() -> load(key, range), loader))
				.join()
				.rows();
	}

	private boolean usable(CompletableFuture<Entry> future) {
		if (future == null || future.isCompletedExceptionally() || future.isCancelled()) {
			return false;
		}

		if (!future.isDone()) {
			return true;
		}

		Entry entry = future.join();
		Duration age = Duration.between(entry.loadedAt(), LocalDateTime.now(APP_ZONE));

		return age.compareTo(entry.closedAtLoad() ? CLOSED_MAX_AGE : RECENT_MAX_AGE) < 0;
	}

	private Entry load(YearMonth month, MonthlyQueryRange range) {
		long started = System.nanoTime();
		LocalDateTime loadedAt = LocalDateTime.now(APP_ZONE);

		List<EnvAgg> rows = repository.envSummary(
				UtilityMonthlyJdbcRepository.ALL_FACS,
				range.from(),
				range.currentTo(),
				range.prevFrom(),
				range.prevTo());

		boolean closed = !loadedAt.isBefore(range.currentTo().plus(CLOSE_GRACE));

		log.info("monthly water/air {} (all facs) loaded: {} rows in {} ms",
				month, rows.size(), (System.nanoTime() - started) / 1_000_000);

		return new Entry(List.copyOf(rows), loadedAt, closed);
	}
}
