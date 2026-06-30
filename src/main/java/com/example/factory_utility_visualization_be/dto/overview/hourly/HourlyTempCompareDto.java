package com.example.factory_utility_visualization_be.dto.overview.hourly;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class HourlyTempCompareDto {

	private Integer scaleHour;

	private BigDecimal yesterday;

	private BigDecimal today;
}