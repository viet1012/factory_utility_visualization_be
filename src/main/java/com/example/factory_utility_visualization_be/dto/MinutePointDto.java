package com.example.factory_utility_visualization_be.dto;

import java.math.*;
import java.time.*;

public record MinutePointDto(
		LocalDateTime ts,
		BigDecimal value,
		String boxDeviceId,
		String plcAddress,
		String cateId
) {}
