package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

import java.time.LocalDateTime;

// ElectricConsumptionRepo.findGridMeterLastPickBefore
public interface ElectricMeterLastPickProjection {
	String getMeter();

	// latest pick_at < :beforeTs (value > 0, MTD), null = never
	LocalDateTime getLastPickAt();
}
