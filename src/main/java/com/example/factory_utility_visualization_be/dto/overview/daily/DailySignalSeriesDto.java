package com.example.factory_utility_visualization_be.dto.overview.daily;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailySignalSeriesDto {

	private String utilityType;

	private String plcAddress;
	private String nameEn;
	private String unit;

	private String aggregation;

	private List<DailySignalPointDto> dailyValues;
}
