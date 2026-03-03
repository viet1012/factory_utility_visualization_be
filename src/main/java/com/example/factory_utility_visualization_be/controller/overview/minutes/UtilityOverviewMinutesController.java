package com.example.factory_utility_visualization_be.controller.overview.minutes;


import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
import com.example.factory_utility_visualization_be.service.overview.minutes.UtilityEnergyMinutesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/utility")
public class UtilityOverviewMinutesController {

	private final UtilityEnergyMinutesService svc;

	// GET /api/utility/energy-minute?facId=Fac_B&minutes=60
	@GetMapping("/energy-minute")
	public List<MinutePointDto> energyMinute(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) Integer minutes,
			@RequestParam(required = false) String nameEn
	) {
		return svc.getEnergyPerMinute(facId, minutes, nameEn);
	}
}