package com.example.factory_utility_visualization_be.service.overview.minutes;


import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
import com.example.factory_utility_visualization_be.repository.overview.minutes.UtilityMinuteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityEnergyMinutesService {
	private final UtilityMinuteRepo repo;

	public List<MinutePointDto> getEnergyPerMinute(String facId, Integer minutes, String nameEn) {
		final String fac = (facId == null || facId.isBlank()) ? "KVH" : facId.trim();
		final int mins = (minutes == null || minutes <= 0) ? 60 : Math.min(minutes, 24 * 60);
		final String metric = (nameEn == null || nameEn.isBlank())
				? "Total Energy Consumption"
				: nameEn.trim();

		return repo.findEnergyDeltaPerMinuteDto(fac, mins, metric);
	}
}