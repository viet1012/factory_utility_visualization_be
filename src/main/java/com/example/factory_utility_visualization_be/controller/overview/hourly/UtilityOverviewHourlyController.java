package com.example.factory_utility_visualization_be.controller.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.UtilityHourlyDashboardDto;
import com.example.factory_utility_visualization_be.service.overview.hourly.UtilityHourlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/utility")
public class UtilityOverviewHourlyController {

	private final UtilityHourlyService service;

	@GetMapping("/hourly-dashboard")
	public ResponseEntity<UtilityHourlyDashboardDto>
	getHourlyDashboard(
			@RequestParam(required = false)
			String facId,

			@RequestParam(required = false)
			String nameEn,

			@RequestParam(required = false)
			BigDecimal exchange,

			@RequestParam(required = false)
			BigDecimal sepzone
	) {
		return ResponseEntity.ok(
				service.getHourlyDashboard(
						facId,
						nameEn,
						exchange,
						sepzone
				)
		);
	}
}
