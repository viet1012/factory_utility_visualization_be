package com.example.factory_utility_visualization_be.controller.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.OverviewMinutePointDto;
import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteDashboardDto;
import com.example.factory_utility_visualization_be.service.overview.minutes.UtilityMinutesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityMinuteController {

	private final UtilityMinutesService service;

	@GetMapping("/minute-dashboard")
	public ResponseEntity<UtilityMinuteDashboardDto>
	getMinuteDashboard(
			@RequestParam(required = false)
			String facId,

			@RequestParam(defaultValue = "60")
			Integer minutes
	) {
		return ResponseEntity.ok(
				service.getMinuteDashboard(
						facId,
						minutes
				)
		);
	}

	/*
	 * Endpoint cũ, giữ tạm trong thời gian chuyển FE.
	 */
	@GetMapping("/energy-minute")
	public ResponseEntity<List<OverviewMinutePointDto>>
	getUtilityPerMinute(
			@RequestParam(required = false)
			String facId,

			@RequestParam(defaultValue = "60")
			Integer minutes,

			@RequestParam
			String type
	) {
		return ResponseEntity.ok(
				service.getUtilityPerMinute(
						facId,
						minutes,
						type
				)
		);
	}
}