package com.example.factory_utility_visualization_be.repository.overview.electric.projection;

// configured grid meter (ElectricConsumptionRepo.findGridMeters)
public interface ElectricConsumptionMeterProjection {
	// box_device_id
	String getMeter();

	// SCADA fac, or FAC_AB / FAC_C from meter-scope
	String getFac();
}
