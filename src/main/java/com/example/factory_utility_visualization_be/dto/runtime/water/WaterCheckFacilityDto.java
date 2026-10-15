package com.example.factory_utility_visualization_be.dto.runtime.water;

import java.util.List;

public record WaterCheckFacilityDto(
		String facId,
		List<WaterCheckGroupDto> groups
) {}
