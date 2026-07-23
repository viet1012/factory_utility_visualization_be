package com.example.factory_utility_visualization_be.dto.overview.daily;

import java.math.BigDecimal;
import java.time.*;

public interface UtilityDailySignalProjection {

	String getBoxDeviceId();

	String getPlcAddress();

	String getNameEn();

	String getUnit();

	LocalDate getRecordDate();

	BigDecimal getAvgValue();

	BigDecimal getMinValue();

	BigDecimal getMaxValue();

	BigDecimal getFirstValue();

	BigDecimal getLastValue();

	BigDecimal getConsumption();

	Long getSampleCount();
}
