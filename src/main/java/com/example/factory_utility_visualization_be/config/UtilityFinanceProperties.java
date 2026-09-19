package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Centralized VND/USD exchange-rate and SEZ ("sepzone") multiplier used by
 * cost-conversion calculations. Each endpoint keeps its own policy value —
 * these are NOT unified, since whether they should be the same value is an
 * open business decision (see Phase G.3 report). This class only moves the
 * existing hardcoded constants into configuration; it does not change any
 * of them.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.finance")
public class UtilityFinanceProperties {

	private Policy hourly = new Policy();
	private Policy solar = new Policy();
	private Policy monthly = new Policy();

	@Getter
	@Setter
	public static class Policy {
		private BigDecimal exchangeRate;
		private BigDecimal sepzone;
	}
}
