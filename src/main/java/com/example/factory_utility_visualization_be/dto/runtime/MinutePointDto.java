package com.example.factory_utility_visualization_be.dto.runtime;

import java.math.*;
import java.time.*;


public record MinutePointDto(
		LocalDateTime ts,
		BigDecimal value,
		String boxDeviceId,
		String plcAddress,
		String cateId,
		String nameEn,
		String nameVi,
		String unit,
		String fac,
		String cate
) {}