package com.example.factory_utility_visualization_be.dto;

public record SumCompareDto(
		String key,
		String nowDate,
		String prevDate,
		Double now,
		Double prev,
		Double delta,
		Double pct,
		String pctText,
		String trend
) {}
