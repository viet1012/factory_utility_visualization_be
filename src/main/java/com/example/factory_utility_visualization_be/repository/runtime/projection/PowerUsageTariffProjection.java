package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;

public interface PowerUsageTariffProjection {
	String getWd();

	BigDecimal getFrTime();

	BigDecimal getToTime();

	BigDecimal getVnd();
}
