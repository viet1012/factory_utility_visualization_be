package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ElectricConsumptionHourProjection {
	String getFac();

	// 1 = SOLAR box, 0 = grid
	Integer getSolar();

	// start of the hour the consumption belongs to
	LocalDateTime getHourAt();

	// 1 = meter-hours dropped as outliers (see ElectricConsumptionRepo)
	Integer getOutlier();

	BigDecimal getKwh();

	// meters (box_device_id) in this row
	Integer getDevices();
}
