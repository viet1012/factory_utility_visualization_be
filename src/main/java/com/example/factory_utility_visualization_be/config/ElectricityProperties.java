package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Single electricity pricing / meter policy for EVERY endpoint that shows
 * money or kWh of electricity (hourly-dashboard, monthly-summary,
 * solar/detail, power-usage, electric-consumption), so they always agree.
 * Rules follow "F2 electric consumption.xlsx":
 *
 * - VND/kWh per hour: time-of-use tariff of F2_Utility_Cost_Master (the only
 *   rate source; see TouTariffService)
 * - grid  $ = kWh x VND x grid.sepzone / grid.exchangeRate       (sheet C40)
 * - solar $ = kWh x VND x solarPriceFactor[band]
 *               x solar.sepzone / solar.exchangeRate              (sheet JI36/JI37)
 * - excludedMeters: box_device_id never counted (duplicate meters)
 * - hourOffset: hours added to hourAt (pick_at - 1h) before the day / tariff
 *   band is chosen (electric-consumption; ?hourOffset overrides it)
 *
 * Every number lives in application.yml (utility.electricity.*) only; there
 * are no Java defaults. The class validates itself: Spring Boot runs a
 * @ConfigurationProperties bean that implements Validator during binding,
 * so a missing / invalid value stops the application at startup with the
 * field path in the message (e.g. "grid.exchangeRate").
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.electricity")
public class ElectricityProperties implements Validator {

	private static final String PREFIX = "utility.electricity.";

	private Policy grid = new Policy();

	private Policy solar = new Policy();

	private SolarPriceFactor solarPriceFactor = new SolarPriceFactor();

	// box_device_id not counted at all (duplicate meters, e.g. P-3.1 = P-3)
	private List<String> excludedMeters = new ArrayList<>();

	// Integer, not int: a missing value must be detected, not default to 0
	private Integer hourOffset;

	@Getter
	@Setter
	public static class Policy {
		// VND per USD, > 0
		private BigDecimal exchangeRate;

		// USD factor on top of VND / exchangeRate, > 0
		private BigDecimal sepzone;
	}

	// share of the grid VND/kWh paid for solar, per tariff band, >= 0
	// (0 allowed: the sheet pays nothing for solar in low peak)
	@Getter
	@Setter
	public static class SolarPriceFactor {
		private BigDecimal normal;
		private BigDecimal highPeak;
		private BigDecimal lowPeak;
	}

	@Override
	public boolean supports(@NonNull Class<?> clazz) {
		return ElectricityProperties.class.isAssignableFrom(clazz);
	}

	@Override
	public void validate(@NonNull Object target, @NonNull Errors errors) {
		ElectricityProperties props = (ElectricityProperties) target;

		policy(props.getGrid(), "grid", errors);
		policy(props.getSolar(), "solar", errors);

		SolarPriceFactor factor = props.getSolarPriceFactor();

		if (factor == null) {
			missing("solarPriceFactor", "solar-price-factor", errors);
		} else {
			notNegative(factor.getNormal(), "solarPriceFactor.normal", "solar-price-factor.normal", errors);
			notNegative(factor.getHighPeak(), "solarPriceFactor.highPeak", "solar-price-factor.high-peak", errors);
			notNegative(factor.getLowPeak(), "solarPriceFactor.lowPeak", "solar-price-factor.low-peak", errors);
		}

		if (props.getHourOffset() == null) {
			missing("hourOffset", "hour-offset", errors);
		}
	}

	private static void policy(Policy policy, String field, Errors errors) {
		if (policy == null) {
			missing(field, field, errors);
			return;
		}

		positive(policy.getExchangeRate(), field + ".exchangeRate", field + ".exchange-rate", errors);
		positive(policy.getSepzone(), field + ".sepzone", field + ".sepzone", errors);
	}

	private static void positive(BigDecimal value, String field, String key, Errors errors) {
		if (value == null) {
			missing(field, key, errors);
		} else if (value.signum() <= 0) {
			errors.rejectValue(field, "positive",
					PREFIX + key + " must be > 0 (was " + value.toPlainString() + ")");
		}
	}

	private static void notNegative(BigDecimal value, String field, String key, Errors errors) {
		if (value == null) {
			missing(field, key, errors);
		} else if (value.signum() < 0) {
			errors.rejectValue(field, "notNegative",
					PREFIX + key + " must be >= 0 (was " + value.toPlainString() + ")");
		}
	}

	private static void missing(String field, String key, Errors errors) {
		errors.rejectValue(field, "required", PREFIX + key + " is required (application.yml)");
	}
}
