package com.example.factory_utility_visualization_be.dto.overview.hourly;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class HourlyTempCompareDto {
	private LocalDateTime hourTime;
	private BigDecimal currentTemp;
	private BigDecimal previousTemp;
	private BigDecimal diffTemp;
}
