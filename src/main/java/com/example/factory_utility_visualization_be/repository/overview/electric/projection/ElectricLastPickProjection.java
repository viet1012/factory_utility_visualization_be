package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

import java.time.LocalDateTime;

// ElectricConsumptionRepo.findLastEnergyPickAt
public interface ElectricLastPickProjection {
	// latest pick_at of an energy row (value > 0, MTD), null = none since :since
	LocalDateTime getLastPickAt();
}
