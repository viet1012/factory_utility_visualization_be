package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches for GET /api/utility/electric-consumption, keyed by month + months
 * + hourOffset. Which one is used is decided by ElectricConsumptionService.
 *
 * - bill period already ended (getClosed): Spring cache
 *   ELECTRIC_CONSUMPTION_CLOSED, long TTL; cleared by
 *   ElectricConsumptionCacheEvictor (EVN import / loss calibration)
 * - bill period still running (getCurrent): kept while the data version
 *   (HourlyEnergyCache.dataVersion = latest energy pick_at) is unchanged, at
 *   most CURRENT_MAX_AGE, like the running month of HourlyEnergyCache.
 *   Single-flight: concurrent requests for a stale / missing key wait for one
 *   computation. clearCurrent() (evictor) also drops results still being
 *   computed from the old EVN / calibration rows (generation).
 *
 * A response is NOT assembled from per-period pieces: the outlier median is
 * taken over the whole request window [first history period, dataUntil), so
 * every monthly[] entry depends on months and dataUntil; reusing a period
 * computed with another window would change the JSON.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectricConsumptionCacheService {

	static final Duration CURRENT_MAX_AGE = Duration.ofMinutes(10);

	private final ElectricConsumptionCalculator calculator;
	private final HourlyEnergyCache hourlyCache;

	private record Computed(
			ElectricConsumptionResponseDto dto,
			// data version read before the computation, null = unknown
			LocalDateTime version,
			long computedAtNanos,
			long generation
	) {}

	private final ConcurrentHashMap<String, CompletableFuture<Computed>> current = new ConcurrentHashMap<>();

	// bumped by clearCurrent(): results of older generations are stale
	private final AtomicLong generation = new AtomicLong();

	// single key format for the caches (full result, with EVN / calibration)
	public static String key(YearMonth month, int months, int hourOffset) {
		return month + "_" + months + "_" + hourOffset;
	}

	// withEvn = false (view=measured): computed without EVN / calibration, own entry
	public static String key(YearMonth month, int months, int hourOffset, boolean withEvn) {
		return withEvn ? key(month, months, hourOffset) : key(month, months, hourOffset) + "_measured";
	}

	public ElectricConsumptionResponseDto getCurrent(YearMonth month, int months, int hourOffset) {
		return getCurrent(month, months, hourOffset, true);
	}

	/**
	 * Running bill period: the cached result while fresh (same data version,
	 * younger than CURRENT_MAX_AGE, no clear since), else computed once for
	 * every waiting request. view=full / official share the withEvn entry.
	 */
	public ElectricConsumptionResponseDto getCurrent(YearMonth month, int months, int hourOffset, boolean withEvn) {
		if (!withEvn) {
			// measured values do not depend on EVN / calibration: reuse a fresh full result
			CompletableFuture<Computed> full = current.get(key(month, months, hourOffset));

			if (full != null && full.isDone() && usable(full)) {
				return full.join().dto();
			}
		}

		String key = key(month, months, hourOffset, withEvn);
		CompletableFuture<Computed> mine = new CompletableFuture<>();
		CompletableFuture<Computed> future = current.compute(key, (k, old) -> usable(old) ? old : mine);

		if (future == mine) {
			long gen = generation.get();
			LocalDateTime version = hourlyCache.dataVersion();

			try {
				ElectricConsumptionResponseDto dto = calculator.calculate(month, months, hourOffset, withEvn);
				mine.complete(new Computed(dto, version, System.nanoTime(), gen));
			} catch (RuntimeException | Error e) {
				current.remove(key, mine);
				mine.completeExceptionally(e);
				throw e;
			}
		}

		try {
			return future.join().dto();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}

			throw e;
		}
	}

	// bill period already ended: cannot change any more (until an EVN import / calibration)
	@Cacheable(
			cacheNames = UtilityCacheNames.ELECTRIC_CONSUMPTION_CLOSED,
			key = "T(com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionCacheService).key(#month, #months, #hourOffset, #withEvn)",
			sync = true
	)
	public ElectricConsumptionResponseDto getClosed(YearMonth month, int months, int hourOffset, boolean withEvn) {
		return calculator.calculate(month, months, hourOffset, withEvn);
	}

	// ElectricConsumptionCacheEvictor: EVN rows / loss calibration changed
	public void clearCurrent() {
		generation.incrementAndGet();
		current.clear();
	}

	// in flight (share it) or computed and still fresh
	private boolean usable(CompletableFuture<Computed> future) {
		if (future == null || future.isCompletedExceptionally() || future.isCancelled()) {
			return false;
		}

		return !future.isDone() || fresh(future.join());
	}

	private boolean fresh(Computed computed) {
		if (computed.generation() != generation.get()) {
			return false;
		}

		if (System.nanoTime() - computed.computedAtNanos() >= CURRENT_MAX_AGE.toNanos()) {
			return false;
		}

		LocalDateTime version = hourlyCache.dataVersion();

		return version == null || Objects.equals(version, computed.version());
	}
}
