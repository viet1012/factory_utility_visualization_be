//package com.example.factory_utility_visualization_be.service.overview.minutes;
//
//
//import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
//import com.example.factory_utility_visualization_be.repository.overview.minutes.UtilityMinuteRepo;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class UtilityEnergyMinutesService {
//	private final UtilityMinuteRepo repo;
//
//	public List<MinutePointDto> getEnergyPerMinute(String facId, Integer minutes, String nameEn) {
//		final String fac = (facId == null || facId.isBlank()) ? "KVH" : facId.trim();
//		final int mins = (minutes == null || minutes <= 0) ? 60 : Math.min(minutes, 24 * 60);
//		final String metric = (nameEn == null || nameEn.isBlank())
//				? "Total Energy Consumption"
//				: nameEn.trim();
//
//		return repo.findUtilityDeltaPerMinuteDto(fac, mins, metric);
//	}
//
//
//}

package com.example.factory_utility_visualization_be.service.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
import com.example.factory_utility_visualization_be.repository.overview.minutes.UtilityMinuteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityMinutesService {

	private final UtilityMinuteRepo repo;

	public List<MinutePointDto> getUtilityPerMinute(
			String facId,
			Integer minutes,
			String type
	) {
		final String fac = (facId == null || facId.isBlank())
				? "KVH"
				: facId.trim();

		final int mins = (minutes == null || minutes <= 0)
				? 60
				: Math.min(minutes, 24 * 60);

		if (!isValidType(type)) {
			return List.of(); // No Data
		}

		return repo.findUtilityDeltaPerMinuteDto(
				fac,
				mins,
				type.trim().toUpperCase()
		);
	}

	private boolean isValidType(String type) {
		if (type == null) return false;

		return switch (type.trim().toUpperCase()) {
			case "ELECTRICITY", "WATER", "AIR" -> true;
			default -> false;
		};
	}


}