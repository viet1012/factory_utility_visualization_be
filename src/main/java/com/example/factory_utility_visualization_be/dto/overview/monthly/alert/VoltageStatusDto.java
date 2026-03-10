package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

public record VoltageStatusDto(
		String name,
		Double minVol,
		Double maxVol,
		String alarm
) {}