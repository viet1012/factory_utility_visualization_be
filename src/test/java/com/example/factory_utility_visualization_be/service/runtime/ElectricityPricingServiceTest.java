package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.ElectricityProperties;
import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shared pricing used by hourly-dashboard, monthly-summary, solar/detail,
 * power-usage and electric-consumption (rules of F2 electric consumption.xlsx).
 */
class ElectricityPricingServiceTest {

	private ElectricityProperties properties;
	private ElectricityPricingService service;

	@BeforeEach
	void setUp() {
		PowerUsageRepo repo = mock(PowerUsageRepo.class);

		when(repo.findTariffs()).thenReturn(List.of(
				tariff("2-7", "4", "9.5", "1833"),
				tariff("2-7", "9.5", "11.5", "3398"),
				tariff("2-7", "11.5", "17", "1833"),
				tariff("2-7", "17", "20", "3398"),
				tariff("2-7", "20", "22", "1833"),
				tariff("2-7", "22", "4", "1190"),
				tariff("1", "4", "22", "1833"),
				tariff("1", "22", "4", "1190")
		));

		properties = new ElectricityProperties();
		properties.setGrid(policy("26005", "1.075"));
		properties.setSolar(policy("26316", "1.0"));
		// no Java defaults any more: the factors come from application.yml only
		properties.getSolarPriceFactor().setNormal(new BigDecimal("0.9"));
		properties.getSolarPriceFactor().setHighPeak(new BigDecimal("0.9"));
		properties.getSolarPriceFactor().setLowPeak(BigDecimal.ZERO);
		properties.setHourOffset(0);

		service = new ElectricityPricingService(new TouTariffService(repo), properties);
	}

	@Test
	void gridUsesTariffTimesSepzoneOverExchange() {
		Pricing pricing = service.load().orElseThrow();

		// Fri 10:00 high peak: 100 kWh * 3398 * 1.075 / 26005 = 14.047 $
		assertThat(scale(sum(pricing, "2026-08-21T10:00", "100", pricing::gridUsd))).isEqualByComparingTo("14.047");
		// grid VND is the same amount before conversion
		assertThat(sum(pricing, "2026-08-21T10:00", "100", pricing::gridVnd)).isEqualByComparingTo("339800");
	}

	@Test
	void solarUsesPriceFactorPerBandAndSolarExchange() {
		Pricing pricing = service.load().orElseThrow();

		// Sat 12:00 normal: 100 kWh * 1833 * 0.9 * 1.0 / 26316 = 6.269 $
		assertThat(scale(sum(pricing, "2026-08-22T12:00", "100", pricing::solarUsd))).isEqualByComparingTo("6.269");
		// Fri 23:00 low peak: factor 0
		assertThat(sum(pricing, "2026-08-21T23:00", "100", pricing::solarUsd)).isEqualByComparingTo("0");
		// solar VND = grid VND x 0.9
		assertThat(sum(pricing, "2026-08-22T12:00", "100", pricing::solarVnd)).isEqualByComparingTo("164970");
	}

	@Test
	void excludedMetersAreUpperCaseAndNeverEmpty() {
		assertThat(service.excludedMeterKeys()).containsExactly("");

		properties.setExcludedMeters(List.of(" p-3.1_mfm384 "));

		assertThat(service.excludedMeterKeys()).containsExactly("P-3.1_MFM384");
	}

	private static BigDecimal sum(
			Pricing pricing,
			String hourAt,
			String kwh,
			BiFunction<BigDecimal, TouShare, BigDecimal> price
	) {
		BigDecimal total = BigDecimal.ZERO;

		for (TouShare share : pricing.shares(LocalDateTime.parse(hourAt))) {
			total = total.add(price.apply(new BigDecimal(kwh), share));
		}

		return total;
	}

	private static BigDecimal scale(BigDecimal value) {
		return value.setScale(3, RoundingMode.HALF_UP);
	}

	private static ElectricityProperties.Policy policy(String rate, String sepzone) {
		ElectricityProperties.Policy policy = new ElectricityProperties.Policy();
		policy.setExchangeRate(new BigDecimal(rate));
		policy.setSepzone(new BigDecimal(sepzone));
		return policy;
	}

	private static PowerUsageTariffProjection tariff(String wd, String fr, String to, String vnd) {
		return new PowerUsageTariffProjection() {
			public String getWd() { return wd; }
			public BigDecimal getFrTime() { return new BigDecimal(fr); }
			public BigDecimal getToTime() { return new BigDecimal(to); }
			public BigDecimal getVnd() { return new BigDecimal(vnd); }
			public java.time.LocalDateTime getEffectiveFrom() { return null; }
			public java.time.LocalDateTime getEffectiveTo() { return null; }
		};
	}
}
