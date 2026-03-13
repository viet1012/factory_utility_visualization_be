package com.example.factory_utility_visualization_be.service.overview.hourly;


import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

public class UtilityEnergyHourlyService {
	private final UtilityHourlyRepo repo;

	public List<HourlyCompareDto> getHourly(String fac, int hours, String nameEn) {
		final String metric = (nameEn == null || nameEn.isBlank())
				? "Total Energy Consumption"
				: nameEn.trim();

		return repo.hourlyCompareDto(fac, hours, metric);
	}
}