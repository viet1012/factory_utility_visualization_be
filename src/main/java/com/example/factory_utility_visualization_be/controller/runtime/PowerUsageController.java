package com.example.factory_utility_visualization_be.controller.runtime;

import com.example.factory_utility_visualization_be.dto.runtime.power.PowerUsageResponseDto;
import com.example.factory_utility_visualization_be.service.runtime.PowerUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class PowerUsageController {

	private final PowerUsageService service;

	// Monitoring > Electricity "Power usage": current period vs the previous
	// one of the same length. Params are validated in the service (400).
	//	http://localhost:9999/api/utility/power-usage?date=2026-10-16&period=day
	@GetMapping("/power-usage")
	public ResponseEntity<PowerUsageResponseDto> powerUsage(
			@RequestParam(required = false) String date,   // yyyy-MM-dd, default today
			@RequestParam(required = false, defaultValue = "day") String period // day|week|month
	) {
		return ResponseEntity.ok(service.getPowerUsage(date, period));
	}
}
