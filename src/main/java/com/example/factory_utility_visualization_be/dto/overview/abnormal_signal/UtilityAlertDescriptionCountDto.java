package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;

import java.util.List;

public record UtilityAlertDescriptionCountDto(
		String parameterCode,
		String ruleType,
		String description,
		long count,
		List<UtilityAlertDeviceCountDto> devices
) {
}
