package com.example.factory_utility_visualization_be.controller.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.service.overview.hourly.UtilityEnergyHourlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/utility")
public class UtilityHourlyController {

	private final UtilityEnergyHourlyService service;


	// VD: 	http://localhost:9999/api/utility/energy-hourly?facId=Fac_B&hours=48
	@GetMapping("/energy-hourly")
	public List<HourlyCompareDto> energyHourly(
			@RequestParam(name = "facId") String facId,
			@RequestParam(name = "hours", defaultValue = "48") int hours
	) {
		return service.getHourly(facId, hours);
	}
}