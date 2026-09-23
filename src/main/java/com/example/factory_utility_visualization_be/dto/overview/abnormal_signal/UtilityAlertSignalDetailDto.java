package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UtilityAlertSignalDetailDto(
		String plcAddress,
		String signalName,
		BigDecimal currentValue,
		BigDecimal previousValue,
		LocalDateTime recordedAt,
		long count
) {
}
