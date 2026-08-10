package com.example.factory_utility_visualization_be.controller.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardDto;
import com.example.factory_utility_visualization_be.service.overview.solar.SolarDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solar")
@RequiredArgsConstructor
public class SolarDashboardController {

	private final SolarDashboardService service;

	@GetMapping("/dashboard")
	public SolarDashboardDto dashboard(

			@RequestParam(defaultValue = "KVH")
			String facId){

		return service.getDashboard(facId);
	}

}