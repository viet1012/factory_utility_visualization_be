package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// one meter x hour (ElectricConsumptionRepo.findMeterHourlyEnergy), no outlier flag
public interface ElectricMeterHourProjection {
	// box_device_id
	String getMeter();

	// DeviceMap fac (SCADA fac or meter-scope FAC_AB / FAC_C)
	String getFac();

	// 1 = SOLAR device, 0 = grid (load) meter
	Integer getSolar();

	// start of the hour = pick_at hour - 1h (hourOffset NOT applied)
	LocalDateTime getHourAt();

	BigDecimal getKwh();

	// latest pick_at of the rows of this meter-hour
	LocalDateTime getLastPickAt();

	// unit of the meter's energy para (F2_Utility_Para.unit)
	String getUnit();
}
