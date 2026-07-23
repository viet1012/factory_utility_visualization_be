package com.example.factory_utility_visualization_be.dto.overview.daily;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UtilityDailyDashboardResponse {

	private String boxDeviceId;
	private String month;

	private LocalDateTime fromTime;
	private LocalDateTime toTime;

	private List<DailySignalSeriesDto> series;
}