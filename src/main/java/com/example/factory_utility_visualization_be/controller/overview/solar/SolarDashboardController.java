package com.example.factory_utility_visualization_be.controller.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarDetailDto;
import com.example.factory_utility_visualization_be.service.overview.solar.SolarDashboardService;
import com.example.factory_utility_visualization_be.service.overview.solar.SolarDetailService;
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

	private final SolarDetailService solarDetailService;

	// ============================================================
	// TODAY
	// ============================================================
	@GetMapping("/today")
	public SolarDashboardDto getTodayDashboard(

			@RequestParam(
					defaultValue = "KVH"
			)
			String facId
	) {

		return service.getDashboard(
				facId
		);
	}

	// ============================================================
	// MONTHLY
	//
	// Example:
	// /api/solar/monthly?facId=FAC_A&month=202608
	// ============================================================
	@GetMapping("/monthly")
	public SolarDashboardDto getMonthlyDashboard(

			@RequestParam(
					defaultValue = "KVH"
			)
			String facId,

			@RequestParam
			String month
	) {

		return service
				.getSolarDashboardByMonth(
						facId,
						month
				);
	}


	@GetMapping("/detail")
	public SolarDetailDto detail(

			@RequestParam(defaultValue = "KVH")
			String facId,

			@RequestParam
			String month
	) {

		return solarDetailService.getDetail(
				facId,
				month
		);
	}
}