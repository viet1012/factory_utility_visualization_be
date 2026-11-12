package com.example.factory_utility_visualization_be.repository.overview.solar.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// solar kWh of the hour starting at hourAt (pick_at - 1h)
public interface SolarHourEnergyProjection {

	LocalDateTime getHourAt();

	BigDecimal getKwh();
}
