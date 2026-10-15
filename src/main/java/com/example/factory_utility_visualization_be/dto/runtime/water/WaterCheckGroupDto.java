package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.util.List;

/**
 * Signals of one facility sharing code (cate_id) and unit. Signals without
 * a category are grouped by name and unit, with code = null.
 */
public record WaterCheckGroupDto(
		String code,
		String name,
		String unit,
		List<WaterCheckThresholdDto> thresholds,
		List<WaterCheckSignalDto> signals
) {}
