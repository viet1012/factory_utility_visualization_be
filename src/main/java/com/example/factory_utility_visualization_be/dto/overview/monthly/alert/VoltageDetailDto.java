package com.example.factory_utility_visualization_be.dto.overview.monthly.alert;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

public record VoltageDetailDto(
		LocalDateTime pickAt,

		Double d108,
		Double d110,
		Double d112,

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