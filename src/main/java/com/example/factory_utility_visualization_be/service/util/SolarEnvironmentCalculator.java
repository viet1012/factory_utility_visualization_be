package com.example.factory_utility_visualization_be.service.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SolarEnvironmentCalculator {

	public static final BigDecimal CO2_FACTOR =
			new BigDecimal("0.6766");

	public static final BigDecimal KG_PER_TREE =
			new BigDecimal("21");

	private SolarEnvironmentCalculator() {
	}

	// CO2 kg = Solar kWh * 0.6766
	public static BigDecimal co2Kg(BigDecimal solarKwh) {
		return solarKwh
				.multiply(CO2_FACTOR)
				.setScale(
						1,
						RoundingMode.HALF_UP
				);
	}

	public static BigDecimal co2Ton(BigDecimal co2Kg) {
		return co2Kg
				.divide(
						new BigDecimal("1000"),
						3,
						RoundingMode.HALF_UP
				);
	}

	public static BigDecimal equivalentTrees(BigDecimal co2Kg) {
		return co2Kg
				.divide(
						KG_PER_TREE,
						0,
						RoundingMode.HALF_UP
				);
	}
}
