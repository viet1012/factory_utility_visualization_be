package com.example.factory_utility_visualization_be.repository.overview.minutes.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtilityMinuteProjection {

	String getUtilityType();

	LocalDateTime getTs();

	BigDecimal getValue();

	String getName();
}