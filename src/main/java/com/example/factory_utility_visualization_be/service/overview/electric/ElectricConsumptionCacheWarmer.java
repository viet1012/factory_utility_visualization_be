package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Fallback heartbeat for the default GET /api/utility/electric-consumption
 * request (current bill month, default months / hourOffset): every
 * warmup-interval it asks the versioned cache, which recomputes only when
 * stale (> 10 min or new data version). The main warm-up after new data is
 * DashboardEnergyWarmer. Disable with
 * utility.electric-consumption.warmup-enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
		prefix = "utility.electric-consumption",
		name = "warmup-enabled",
		havingValue = "true",
		matchIfMissing = true
)
public class ElectricConsumptionCacheWarmer {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final ElectricConsumptionCalculator calculator;
	private final ElectricConsumptionCacheService cacheService;
	private final ElectricConsumptionProperties properties;

	@Scheduled(
			initialDelayString = "${utility.electric-consumption.warmup-initial-delay:PT20S}",
			fixedDelayString = "${utility.electric-consumption.warmup-interval:PT10M}"
	)
	public void warmUp() {
		YearMonth month = calculator.billMonthOf(LocalDate.now(APP_ZONE));
		int months = properties.getDefaultMonths();
		int offset = calculator.defaultHourOffset();
		long started = System.nanoTime();

		try {
			// recomputes only when stale (new data version / older than 10 min)
			cacheService.getCurrent(month, months, offset);
			log.info("electric-consumption cache warm-up {} took {} ms",
					ElectricConsumptionCacheService.key(month, months, offset),
					(System.nanoTime() - started) / 1_000_000);
		} catch (RuntimeException e) {
			// keep the previous cached value; next run retries
			log.warn("electric-consumption cache warm-up {} failed after {} ms: {}",
					ElectricConsumptionCacheService.key(month, months, offset),
					(System.nanoTime() - started) / 1_000_000, e.toString());
		}
	}
}
