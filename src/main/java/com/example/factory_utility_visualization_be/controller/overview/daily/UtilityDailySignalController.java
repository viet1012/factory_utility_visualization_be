package com.example.factory_utility_visualization_be.controller.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardResponse;
import com.example.factory_utility_visualization_be.service.overview.daily.UtilityDailySignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityDailySignalController {

	private final UtilityDailySignalService service;

	@GetMapping("/daily-signals")
	public ResponseEntity<UtilityDailyDashboardResponse>
	getDailySignals(
			@RequestParam String boxDeviceId,
			@RequestParam String month
	) {
		return ResponseEntity.ok(
				service.getDailySignals(
						boxDeviceId,
						month
				)
		);
	}
}