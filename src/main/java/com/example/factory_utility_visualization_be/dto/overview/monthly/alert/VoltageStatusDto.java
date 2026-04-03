package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class VoltageStatusDto {
	private String facId;
	private String boxDeviceId;
	private String cateId;
	private double minVol;
	private double maxVol;
	private double minVolStd;
	private double maxVolStd;
	private String alarm;
	private OffsetDateTime updatedAt;

	public VoltageStatusDto(
			String facId,
			String boxDeviceId,
			String cateId,
			double minVol,
			double maxVol,
			double minVolStd,
			double maxVolStd,
			String alarm,
			OffsetDateTime updatedAt
	) {
		this.facId = facId;
		this.boxDeviceId = boxDeviceId;
		this.cateId = cateId;
		this.minVol = minVol;
		this.maxVol = maxVol;
		this.minVolStd = minVolStd;
		this.maxVolStd = maxVolStd;
		this.alarm = alarm;
		this.updatedAt = updatedAt;
	}
}