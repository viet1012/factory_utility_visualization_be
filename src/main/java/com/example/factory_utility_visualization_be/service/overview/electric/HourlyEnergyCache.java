package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricLastPickProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricMeterHourProjection;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.MeterFilter;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * kWh per meter x hour of History_Main, cached per calendar month (by hour
 * start = pick_at - 1h, before hourOffset), for every DeviceMap meter (grid +
 * SOLAR, every fac). KVH / Fac_A / Fac_B / Fac_C and the dashboards
 * (monthly-summary, solar/monthly, energy-daily) all read the same cached
 * month: one SQL per month instead of one per request x fac.
 *
 * Freshness:
 * - month already closed when it was loaded: kept CLOSED_MAX_AGE
 * - month still running: kept while the data version (latest energy pick_at,
 *   see refreshVersion) is unchanged, at most CURRENT_MAX_AGE
 *
 * Single-flight: concurrent requests for a month that is loading wait for the
 * same future (AsyncCache asMap().compute only swaps the future, the SQL runs
 * on the loader pool, at most LOADER_THREADS months at a time). A failed load
 * is removed from the cache by Caffeine, the next request retries.
 */
@Slf4j
@Service
public class HourlyEnergyCache {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	static final Duration CURRENT_MAX_AGE = Duration.ofMinutes(10);
	static final Duration CLOSED_MAX_AGE = Duration.ofHours(12);

	// a month counts as closed once its last hour's rows (pick_at = month end
	// + 1h) should have arrived
	private static final Duration CLOSE_GRACE = Duration.ofHours(2);

	// how far back the version query looks for the latest pick_at
	private static final Duration VERSION_LOOKBACK = Duration.ofDays(2);

	private static final int LOADER_THREADS = 2;

	private static final int MAX_MONTHS = 15;

	/**
	 * one meter x hour; fac = upper-case DeviceMap fac, hourAt = pick_at hour
	 * - 1h, lastPickAt = latest pick_at of its rows, unit = energy para unit
	 */
	public record MeterHour(
			String meter,
			String fac,
			boolean solar,
			LocalDateTime hourAt,
			BigDecimal kwh,
			LocalDateTime lastPickAt,
			String unit
	) {}

	private record MonthData(
			YearMonth month,
			List<MeterHour> rows,
			// data version (latest energy pick_at) read just before the load
			LocalDateTime version,
			LocalDateTime loadedAt,
			boolean closedAtLoad
	) {}

	private final ElectricConsumptionRepo repo;
	private final GridSolarSplit split;

	private final ExecutorService loader;
	private final AsyncCache<YearMonth, MonthData> cache;

	// latest energy pick_at seen (refreshVersion), null = unknown yet
	private volatile LocalDateTime dataVersion;

	public HourlyEnergyCache(ElectricConsumptionRepo repo, GridSolarSplit split) {
		this.repo = repo;
		this.split = split;

		AtomicInteger threads = new AtomicInteger();
		this.loader = Executors.newFixedThreadPool(LOADER_THREADS, runnable -> {
			Thread thread = new Thread(runnable, "hourly-energy-loader-" + threads.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});

		this.cache = Caffeine.newBuilder()
				.maximumSize(MAX_MONTHS)
				.buildAsync();
	}

	@PreDestroy
	void shutdown() {
		loader.shutdownNow();
	}

	/**
	 * Meter-hours with hourAt in [rawFrom, rawTo) (hour start before
	 * hourOffset). Loads / reuses every month the range touches; months are
	 * loaded in parallel, a month already loading is waited for.
	 */
	public List<MeterHour> rows(LocalDateTime rawFrom, LocalDateTime rawTo) {
		List<MeterHour> rows = new ArrayList<>();

		if (!rawFrom.isBefore(rawTo)) {
			return rows;
		}

		List<CompletableFuture<MonthData>> futures = new ArrayList<>();

		for (YearMonth m = YearMonth.from(rawFrom); !m.atDay(1).atStartOfDay().isAfter(rawTo.minusNanos(1)); m = m.plusMonths(1)) {
			futures.add(month(m));
		}

		for (CompletableFuture<MonthData> future : futures) {
			for (MeterHour row : future.join().rows()) {
				if (!row.hourAt().isBefore(rawFrom) && row.hourAt().isBefore(rawTo)) {
					rows.add(row);
				}
			}
		}

		return rows;
	}

	// warm-up: load (or keep, when still fresh) these months, waiting for them
	public void preload(List<YearMonth> months) {
		List<CompletableFuture<MonthData>> futures = months.stream().map(this::month).toList();
		futures.forEach(CompletableFuture::join);
	}

	/**
	 * Reads the data version (latest energy pick_at of the last
	 * VERSION_LOOKBACK). A running month loaded with another version is
	 * reloaded on its next use. Returns the version (null = no data).
	 */
	public LocalDateTime refreshVersion() {
		MeterFilter filter = split.meterFilter();
		ElectricLastPickProjection last = repo.findLastEnergyPickAt(
				GridSolarSplit.ENERGY_NAME,
				LocalDateTime.now(APP_ZONE).minus(VERSION_LOOKBACK),
				filter.excluded(), filter.facAb(), filter.facC());
		LocalDateTime version = last == null ? null : last.getLastPickAt();

		if (version != null) {
			dataVersion = version;
		}

		return version;
	}

	public LocalDateTime dataVersion() {
		return dataVersion;
	}

	private CompletableFuture<MonthData> month(YearMonth month) {
		return cache.asMap().compute(month, (key, old) -> usable(old)
				? old
				: CompletableFuture.supplyAsync(() -> load(key), loader));
	}

	// in flight (share it) or loaded and still fresh
	private boolean usable(CompletableFuture<MonthData> future) {
		if (future == null || future.isCompletedExceptionally() || future.isCancelled()) {
			return false;
		}

		return !future.isDone() || fresh(future.join());
	}

	private boolean fresh(MonthData data) {
		Duration age = Duration.between(data.loadedAt(), LocalDateTime.now(APP_ZONE));

		if (data.closedAtLoad()) {
			return age.compareTo(CLOSED_MAX_AGE) < 0;
		}

		LocalDateTime version = dataVersion;

		return age.compareTo(CURRENT_MAX_AGE) < 0
				&& (version == null || version.equals(data.version()));
	}

	private MonthData load(YearMonth month) {
		long started = System.nanoTime();
		LocalDateTime loadedAt = LocalDateTime.now(APP_ZONE);
		LocalDateTime monthStart = month.atDay(1).atStartOfDay();
		LocalDateTime monthEnd = month.plusMonths(1).atDay(1).atStartOfDay();
		boolean closed = !loadedAt.isBefore(monthEnd.plusHours(1).plus(CLOSE_GRACE));

		// version first: data arriving during the load makes it stale at once
		LocalDateTime version = closed ? null : refreshVersionQuietly();
		long versionMs = (System.nanoTime() - started) / 1_000_000;

		MeterFilter filter = split.meterFilter();
		// rows are stamped at the end of their hour: hour h <-> pick_at in [h + 1h, h + 2h)
		List<ElectricMeterHourProjection> result = repo.findMeterHourlyEnergy(
				GridSolarSplit.ENERGY_NAME, monthStart.plusHours(1), monthEnd.plusHours(1),
				filter.excluded(), filter.facAb(), filter.facC());
		long queryMs = (System.nanoTime() - started) / 1_000_000 - versionMs;

		List<MeterHour> rows = new ArrayList<>(result.size());

		for (ElectricMeterHourProjection row : result) {
			if (row.getMeter() == null || row.getHourAt() == null || row.getKwh() == null) {
				continue;
			}

			// interned: the same few meter / fac / unit strings on every row
			rows.add(new MeterHour(
					row.getMeter().trim().intern(),
					GridSolarSplit.facKey(row.getFac()).intern(),
					row.getSolar() != null && row.getSolar() == 1,
					row.getHourAt(),
					row.getKwh(),
					row.getLastPickAt(),
					row.getUnit() == null ? null : row.getUnit().intern()));
		}

		log.info("hourly energy {} loaded: {} meter-hours, version query {} ms, hourly query {} ms, total {} ms ({})",
				month, rows.size(), versionMs, queryMs, (System.nanoTime() - started) / 1_000_000,
				closed ? "closed" : "running, version " + version);

		return new MonthData(month, List.copyOf(rows), version, loadedAt, closed);
	}

	private LocalDateTime refreshVersionQuietly() {
		try {
			return refreshVersion();
		} catch (RuntimeException e) {
			log.warn("hourly energy version query failed: {}", e.toString());
			return dataVersion;
		}
	}
}
