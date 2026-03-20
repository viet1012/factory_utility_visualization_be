package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

public record VoltageDetailDto(
		LocalDateTime pickAt,

		Double d12,
		Double d14,
		Double d16,

		String alarm,
		LocalDateTime timestamp
) {
}

//@Data
//@AllArgsConstructor
//public class VoltageDetailDto1 {
//	private LocalDateTime time;
//	private Map<String, Double> values;
//	private String alarm;
//	private LocalDateTime updatedAt;
//}