package com.example.factory_utility_visualization_be.dto.overview.daily;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailySignalPointDto {

	private LocalDate recordDate;

	private BigDecimal avgValue;
	private BigDecimal minValue;
	private BigDecimal maxValue;

	private BigDecimal firstValue;
	private BigDecimal lastValue;

	private BigDecimal consumption;

	private Long sampleCount;
}