package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import java.time.LocalDateTime;


public record VoltageDetailDto(
		LocalDateTime recordedMinute,
		String cateId,
		String boxDeviceId,
		Double minVol,
		Double maxVol,
		Double minVolStd,
		Double maxVolStd,
		String alarm,
		LocalDateTime updatedAt
) {
}