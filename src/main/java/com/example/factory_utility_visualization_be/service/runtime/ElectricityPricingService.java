package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.ElectricityProperties;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouTariff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one place electricity money is priced (see ElectricityProperties for
 * the rules). Java callers use {@link #load()}; SQL-priced queries
 * (hourly-dashboard, monthly-summary) take the grid exchange rate / sepzone
 * from here and the same F2_Utility_Cost_Master tariff.
 */
@Service
@RequiredArgsConstructor
public class ElectricityPricingService {

	private static final int FACTOR_SCALE = 12;

	private final TouTariffService tariffService;
	private final ElectricityProperties properties;

	/**
	 * Tariff + conversion factors of one request.
	 *
	 * @param gridFactor     USD per VND for grid energy (sepzone / exchangeRate)
	 * @param solarVndFactor share of the grid VND paid for solar, per band
	 * @param solarFactor    USD per grid VND for solar energy, per band
	 */
	public record Pricing(
			TouTariff tariff,
			BigDecimal gridFactor,
			Map<TouBand, BigDecimal> solarVndFactor,
			Map<TouBand, BigDecimal> solarFactor
	) {
		public List<TouShare> shares(LocalDateTime hourAt) {
			return tariff.shares(hourAt);
		}

		// VND of the part of kwh in this share at the grid rate
		public BigDecimal gridVnd(BigDecimal kwh, TouShare share) {
			return kwh.multiply(share.fraction()).multiply(share.vnd());
		}

		public BigDecimal gridUsd(BigDecimal kwh, TouShare share) {
			return gridVnd(kwh, share).multiply(gridFactor);
		}

		// VND of the part of kwh in this share at the solar price
		public BigDecimal solarVnd(BigDecimal kwh, TouShare share) {
			return gridVnd(kwh, share).multiply(solarVndFactor.get(share.band()));
		}

		public BigDecimal solarUsd(BigDecimal kwh, TouShare share) {
			return gridVnd(kwh, share).multiply(solarFactor.get(share.band()));
		}

		// VND/kWh of a band on a Monday..Saturday
		public Optional<BigDecimal> weekdayVnd(TouBand band) {
			return tariff.weekdayRate(band);
		}
	}

	// empty when the tariff cannot be mapped to 3 bands or a policy is missing
	public Optional<Pricing> load() {
		Optional<BigDecimal> gridFactor = factor(properties.getGrid());
		Optional<BigDecimal> solarBase = factor(properties.getSolar());
		ElectricityProperties.SolarPriceFactor price = properties.getSolarPriceFactor();

		if (gridFactor.isEmpty() || solarBase.isEmpty() || price == null
				|| price.getNormal() == null || price.getHighPeak() == null || price.getLowPeak() == null) {
			return Optional.empty();
		}

		Map<TouBand, BigDecimal> solarVndFactor = new EnumMap<>(TouBand.class);
		solarVndFactor.put(TouBand.NORMAL, price.getNormal());
		solarVndFactor.put(TouBand.HIGH_PEAK, price.getHighPeak());
		solarVndFactor.put(TouBand.LOW_PEAK, price.getLowPeak());

		Map<TouBand, BigDecimal> solarFactor = new EnumMap<>(TouBand.class);
		solarVndFactor.forEach((band, value) -> solarFactor.put(band, value.multiply(solarBase.get())));

		return tariffService.load()
				.map(tariff -> new Pricing(tariff, gridFactor.get(), solarVndFactor, solarFactor));
	}

	// grid exchange rate / sepzone for SQL-priced queries
	public BigDecimal gridExchangeRate() {
		return properties.getGrid().getExchangeRate();
	}

	public BigDecimal gridSepzone() {
		return properties.getGrid().getSepzone();
	}

	// grid VND -> USD (null when vnd is null or the policy is missing)
	public BigDecimal gridVndToUsd(BigDecimal vnd) {
		return vnd == null ? null : factor(properties.getGrid()).map(vnd::multiply).orElse(null);
	}

	// upper-case excluded box_device_id list for SQL IN; List.of("") when empty
	public List<String> excludedMeterKeys() {
		return meterKeys(properties.getExcludedMeters());
	}

	// trimmed upper-case box_device_id for the SQL IN lists; List.of("") when
	// empty (an empty IN list is invalid SQL, '' matches no meter)
	public static List<String> meterKeys(List<String> meters) {
		if (meters == null) {
			return List.of("");
		}

		List<String> keys = meters.stream()
				.filter(meter -> meter != null && !meter.isBlank())
				.map(meter -> meter.trim().toUpperCase(Locale.ROOT))
				.distinct()
				.toList();

		return keys.isEmpty() ? List.of("") : keys;
	}

	// sepzone / exchangeRate
	private static Optional<BigDecimal> factor(ElectricityProperties.Policy policy) {
		if (policy == null || policy.getExchangeRate() == null || policy.getExchangeRate().signum() == 0
				|| policy.getSepzone() == null) {
			return Optional.empty();
		}

		return Optional.of(policy.getSepzone().divide(policy.getExchangeRate(), FACTOR_SCALE, RoundingMode.HALF_UP));
	}
}
