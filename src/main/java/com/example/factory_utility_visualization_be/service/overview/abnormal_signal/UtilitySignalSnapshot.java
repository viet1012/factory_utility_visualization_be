package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UtilitySignalSnapshot(
		String fac,
		String scadaId,
		String cate,
		Long paraId,
		String parameterCode,
		String signalName,
		String unit,
		String boxDeviceId,
		String plcAddress,
		LocalDateTime recordedAt,
		BigDecimal currentValue,
		BigDecimal prevValue
) {
}
