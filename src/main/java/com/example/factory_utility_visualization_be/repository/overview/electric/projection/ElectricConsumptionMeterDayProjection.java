package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

import java.time.LocalDateTime;

// grid meter x day (ElectricConsumptionRepo.findMeterDays)
public interface ElectricConsumptionMeterDayProjection {
	// box_device_id
	String getMeter();

	String getFac();

	// start of the day (hourAt incl. hourOffset), null = meter has no data in the period
	LocalDateTime getDayAt();

	// hours with valid (non-outlier) data
	Integer getValidHours();

	// hours dropped as outliers
	Integer getOutlierHours();

	// last valid pick_at of the meter (not shifted by hourOffset), null = never
	LocalDateTime getLastPickAt();
}
