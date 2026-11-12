package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.config.ElectricityProperties;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouTariff;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The ONLY place electricity money is priced (see ElectricityProperties for
 * the rules). Every endpoint (hourly-dashboard, monthly-summary, solar,
 * power-usage, electric-consumption) loads kWh per hour from SQL and prices
 * it here via {@link #load()}; no SQL query computes money.
 *
 * An hour whose date has no tariff version has no price: its money is null
 * (never 0) and period totals that contain such an hour are flagged
 * (priceMissing) by the caller.
 */
@Service
@RequiredArgsConstructor
public class ElectricityPricingService {

	private static final int FACTOR_SCALE = 12;

	private final TouTariffService tariffService;
	private final ElectricityProperties properties;

	// unrounded VND / USD
	public record Money(BigDecimal vnd, BigDecimal usd) {
		public Money add(Money other) {
			return new Money(vnd.add(other.vnd), usd.add(other.usd));
		}
	}

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
		// shares of the hour starting at hourAt, from the tariff version in
		// effect on hourAt.toLocalDate(); empty = that day has no price, so
		// gridUsd / solarUsd (computed per share) add nothing for it
		public List<TouShare> shares(LocalDateTime hourAt) {
			return tariff.shares(hourAt);
		}

		// a tariff version is in effect on day
		public boolean priced(LocalDate day) {
			return tariff.version(day).isPresent();
		}

		// grid VND / USD of kwh used in the hour starting at hourAt;
		// empty = no tariff version on that day (money must be null, not 0)
		public Optional<Money> gridCost(LocalDateTime hourAt, BigDecimal kwh) {
			List<TouShare> shares = shares(hourAt);

			if (shares.isEmpty()) {
				return Optional.empty();
			}

			BigDecimal vnd = BigDecimal.ZERO;
			BigDecimal usd = BigDecimal.ZERO;

			for (TouShare share : shares) {
				vnd = vnd.add(gridVnd(kwh, share));
				usd = usd.add(gridUsd(kwh, share));
			}

			return Optional.of(new Money(vnd, usd));
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

		// VND/kWh of a band on a Monday..Saturday, version in effect on day
		public Optional<BigDecimal> weekdayVnd(TouBand band, LocalDate day) {
			return tariff.weekdayRate(band, day);
		}

		// effective_from of the version in effect on day (empty = no version or
		// a version without lower bound)
		public Optional<LocalDate> effectiveFrom(LocalDate day) {
			return tariff.version(day).map(TouVersion::effectiveFrom);
		}
	}

	// empty only when F2_Utility_Cost_Master has no usable tariff version; the
	// factors are always present (ElectricityProperties is validated at startup)
	public Optional<Pricing> load() {
		BigDecimal gridFactor = factor(properties.getGrid());
		BigDecimal solarBase = factor(properties.getSolar());
		ElectricityProperties.SolarPriceFactor price = properties.getSolarPriceFactor();

		Map<TouBand, BigDecimal> solarVndFactor = new EnumMap<>(TouBand.class);
		solarVndFactor.put(TouBand.NORMAL, price.getNormal());
		solarVndFactor.put(TouBand.HIGH_PEAK, price.getHighPeak());
		solarVndFactor.put(TouBand.LOW_PEAK, price.getLowPeak());

		Map<TouBand, BigDecimal> solarFactor = new EnumMap<>(TouBand.class);
		solarVndFactor.forEach((band, value) -> solarFactor.put(band, value.multiply(solarBase)));

		return tariffService.load()
				.map(tariff -> new Pricing(tariff, gridFactor, solarVndFactor, solarFactor));
	}

	// hour shift applied to every hourAt (pick_at - 1h) before day / tariff band
	public int hourOffset() {
		return properties.getHourOffset();
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

	// sepzone / exchangeRate (both validated non-null, > 0)
	private static BigDecimal factor(ElectricityProperties.Policy policy) {
		return policy.getSepzone().divide(policy.getExchangeRate(), FACTOR_SCALE, RoundingMode.HALF_UP);
	}
}
