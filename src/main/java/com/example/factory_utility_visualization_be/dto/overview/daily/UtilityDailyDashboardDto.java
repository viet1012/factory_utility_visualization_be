package com.example.factory_utility_visualization_be.dto.overview.daily;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilityDailyDashboardDto {

	private String facId;
	private String month;

	private List<DailyDto> electricity;
	private List<DailyDto> water;
	private List<DailyDto> air;
}