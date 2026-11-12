package com.example.factory_utility_visualization_be.repository.overview.hourly.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// grid kWh of one hour (UtilityHourlyRepo.findHourlyElectricity), no money
public interface HourlyEnergyProjection {

	// day of the hour (00:00)
	LocalDateTime getRecordDate();

	// 0..23, hour = pick_at - 1h
	Integer getHourNumber();

	BigDecimal getKwh();
}
