package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AbnormalSignalDto(

		String signalName,
		String plcAddress,

		BigDecimal currentValue,
		BigDecimal prevValue,
		BigDecimal jumpSize,

		String status,
		String description,

		LocalDateTime recordedAt

) {
}