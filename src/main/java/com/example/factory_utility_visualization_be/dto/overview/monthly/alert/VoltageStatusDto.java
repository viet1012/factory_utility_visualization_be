package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record VoltageStatusDto(
		String fac,
		String boxDeviceId,
		String name,
		Double minVol,
		Double maxVol,
		String alarm,
		OffsetDateTime timestamp
) {}