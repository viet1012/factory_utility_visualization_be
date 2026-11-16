package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.service.overview.monthly.UtilityEnergyMonthlyService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pre-computes the Overview dashboards so the first FE call does not wait
 * for History_Main:
 * - at startup (ApplicationReadyEvent, in the background, never blocks it)
 * - whenever the data version changes (latest energy pick_at, polled every
 *   utility.electric-consumption.dashboard-version-check)
 *
 * One run: hourly months (current - 2 .. current, the windows of
 * monthly-summary current + previous month) into HourlyEnergyCache, then
 * monthly-summary of KVH + Fac_A/B/C for the current month (refreshed) and
 * the previous month (cached if absent). solar/monthly and energy-daily have
 * no response cache; they only need the hourly months, already loaded.
 * After the dashboards: electric-consumption (hourly months of the current
 * bill period + defaultMonths previous periods, then the default response).
 *
 * One run at a time (a version change during a run is picked up by the next
 * poll). Errors are only logged: requests still work, they then load on
 * demand. Disable with utility.electric-consumption.dashboard-warmup-enabled=false.
 */
@Slf4j
@Component
@ConditionalOnProperty(
		prefix = "utility.electric-consumption",
		name = "dashboard-warmup-enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class DashboardEnergyWarmer {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private static final List<String> FACS = List.of("KVH", "Fac_A", "Fac_B", "Fac_C");

	private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

	private final HourlyEnergyCache hourlyCache;
	private final UtilityEnergyMonthlyService monthlyService;
	private final ElectricConsumptionCalculator electricCalculator;
	private final ElectricConsumptionCacheService electricCache;
	private final ElectricConsumptionProperties electricProperties;

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "dashboard-energy-warmer");
		thread.setDaemon(true);
		return thread;
	});

	private final AtomicBoolean running = new AtomicBoolean();

	// data version of the last successful run
	private volatile LocalDateTime warmedVersion;

	public DashboardEnergyWarmer(
			HourlyEnergyCache hourlyCache,
			UtilityEnergyMonthlyService monthlyService,
			ElectricConsumptionCalculator electricCalculator,
			ElectricConsumptionCacheService electricCache,
			ElectricConsumptionProperties electricProperties
	) {
		this.hourlyCache = hourlyCache;
		this.monthlyService = monthlyService;
		this.electricCalculator = electricCalculator;
		this.electricCache = electricCache;
		this.electricProperties = electricProperties;
	}

	@PreDestroy
	void shutdown() {
		worker.shutdownNow();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		submit("startup");
	}

	@Scheduled(
			initialDelayString = "${utility.electric-consumption.dashboard-version-check:PT1M}",
			fixedDelayString = "${utility.electric-consumption.dashboard-version-check:PT1M}"
	)
	public void checkVersion() {
		try {
			LocalDateTime version = hourlyCache.refreshVersion();

			if (version != null && !version.equals(warmedVersion)) {
				submit("new data, latest pick_at " + version);
			}
		} catch (RuntimeException e) {
			log.warn("dashboard version check failed: {}", e.toString());
		}
	}

	private void submit(String reason) {
		if (!running.compareAndSet(false, true)) {
			return;
		}

		try {
			worker.execute(() -> {
				try {
					warmUp(reason);
				} finally {
					running.set(false);
				}
			});
		} catch (RuntimeException e) {
			running.set(false);
			log.warn("dashboard warm-up not started ({}): {}", reason, e.toString());
		}
	}

	/*
	 * Current month FIRST, previous month after, every step sequential (one
	 * SQL at a time from the warmer), so a first FE request (almost always
	 * the current month) finds its data early and competes with as little
	 * warm-up work as possible:
	 * 1. hourly months of the current month's windows: current, then previous
	 *    (monthly-summary current = [prev month start, now))
	 * 2. monthly-summary current month, KVH + Fac_A/B/C (refreshed)
	 * 3. hourly month current - 2 (window of the previous month's summary)
	 * 4. monthly-summary previous month, KVH + Fac_A/B/C (cached if absent;
	 *    water / air of the closed month: one SQL for every fac, MonthlyEnvCache)
	 */
	private void warmUp(String reason) {
		long started = System.nanoTime();
		YearMonth current = YearMonth.now(APP_ZONE);
		YearMonth previous = current.minusMonths(1);
		boolean ok = true;

		LocalDateTime version = hourlyCache.dataVersion();

		ok &= step(reason, "hourly " + current, () -> hourlyCache.preload(List.of(current)));
		ok &= step(reason, "hourly " + previous, () -> hourlyCache.preload(List.of(previous)));

		for (String fac : FACS) {
			ok &= step(reason, "monthly-summary " + fac + " " + current,
					() -> monthlyService.refreshMonthlySummary(fac, current.format(YYYYMM)));
		}

		long currentMs = (System.nanoTime() - started) / 1_000_000;

		ok &= step(reason, "hourly " + current.minusMonths(2),
				() -> hourlyCache.preload(List.of(current.minusMonths(2))));

		for (String fac : FACS) {
			ok &= step(reason, "monthly-summary " + fac + " " + previous,
					() -> monthlyService.getMonthlySummary(fac, previous.format(YYYYMM)));
		}

		long dashboardMs = (System.nanoTime() - started) / 1_000_000;

		ok &= warmElectricConsumption(reason);

		if (ok) {
			// the version read before the run: newer data triggers another run
			warmedVersion = version != null ? version : hourlyCache.dataVersion();
		}

		log.info("dashboard warm-up ({}) {}: current month {} ms, dashboards {} ms, total {} ms",
				reason, ok ? "done" : "partly failed", currentMs, dashboardMs,
				(System.nanoTime() - started) / 1_000_000);
	}

	/*
	 * 5. electric-consumption (after the dashboards): hourly months of the
	 *    current bill period + defaultMonths previous periods (= the window of
	 *    the default request), newest first, then the default response itself
	 *    (current bill month, default months / hourOffset) into the versioned
	 *    cache (recomputed only when stale).
	 */
	private boolean warmElectricConsumption(String reason) {
		boolean ok = true;
		LocalDateTime now = LocalDateTime.now(APP_ZONE);
		YearMonth billMonth = electricCalculator.billMonthOf(now.toLocalDate());
		int months = electricProperties.getDefaultMonths();
		int offset = electricCalculator.defaultHourOffset();

		LocalDateTime windowFrom = electricCalculator.period(billMonth.minusMonths(months)).from().atStartOfDay();
		LocalDate historyStart = electricProperties.getHistoryStartDate();

		if (historyStart != null && windowFrom.isBefore(historyStart.atStartOfDay())) {
			windowFrom = historyStart.atStartOfDay();
		}

		// cached months are keyed by raw hour start (before hourOffset)
		YearMonth first = YearMonth.from(windowFrom.minusHours(offset));

		for (YearMonth m = YearMonth.from(now.minusHours(offset)); !m.isBefore(first); m = m.minusMonths(1)) {
			YearMonth month = m;
			ok &= step(reason, "hourly " + month + " (electric-consumption)",
					() -> hourlyCache.preload(List.of(month)));
		}

		ok &= step(reason, "electric-consumption " + ElectricConsumptionCacheService.key(billMonth, months, offset),
				() -> electricCache.getCurrent(billMonth, months, offset));

		return ok;
	}

	// runs one warm-up step; a failure is only logged
	private boolean step(String reason, String name, Runnable action) {
		long started = System.nanoTime();

		try {
			action.run();
			log.debug("dashboard warm-up ({}): {} took {} ms", reason, name, (System.nanoTime() - started) / 1_000_000);
			return true;
		} catch (RuntimeException e) {
			log.warn("dashboard warm-up ({}): {} failed after {} ms: {}",
					reason, name, (System.nanoTime() - started) / 1_000_000, e.toString());
			return false;
		}
	}
}
