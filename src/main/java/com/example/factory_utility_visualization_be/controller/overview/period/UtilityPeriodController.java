package com.example.factory_utility_visualization_be.controller.overview.period;


import com.example.factory_utility_visualization_be.dto.overview.period.UtilityPeriodDashboardDto;
import com.example.factory_utility_visualization_be.service.overview.period.UtilityPeriodService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
		"/api/utility"
)
@RequiredArgsConstructor
public class UtilityPeriodController {

	private final UtilityPeriodService service;

	@GetMapping(
			"/period-dashboard"
	)
	public UtilityPeriodDashboardDto getDashboard(

			@RequestParam(
					defaultValue = "KVH"
			)
			String facId,

			@RequestParam(
					defaultValue = "ELECTRICITY"
			)
			String type,

			@RequestParam(
					defaultValue = "WEEK"
			)
			String period,

			@RequestParam(
					required = false
			)
			String date
	) {

		return service.getDashboard(
				facId,
				type,
				period,
				date
		);
	}
}