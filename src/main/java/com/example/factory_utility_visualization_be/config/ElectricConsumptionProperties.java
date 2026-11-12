package com.example.factory_utility_visualization_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * GET /api/utility/electric-consumption, same rules as the
 * "F2 electric consumption.xlsx" daily monitoring sheets:
 * - bill period of month M = [day billingCutoffDay of M-1, day billingCutoffDay of M)
 *   (e.g. Sep = Aug 21 ~ Sep 20)
 * - grid U.P [$/kWh] = tariff VND * grid.sepzone / grid.exchangeRate
 * - solar U.P [$/kWh] = tariff VND * solarPriceFactor * solar.sepzone / solar.exchangeRate
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "utility.electric-consumption")
public class ElectricConsumptionProperties {

	private int billingCutoffDay = 21;

	private int defaultMonths = 12;

	private int maxMonths = 36;

	private BigDecimal solarPriceFactor = new BigDecimal("0.9");

	private UtilityFinanceProperties.Policy grid = new UtilityFinanceProperties.Policy();

	private UtilityFinanceProperties.Policy solar = new UtilityFinanceProperties.Policy();
}
