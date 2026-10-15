package com.example.factory_utility_visualization_be.controller.runtime;

import com.example.factory_utility_visualization_be.dto.runtime.water.WaterCheckResponseDto;
import com.example.factory_utility_visualization_be.service.runtime.WaterCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class WaterCheckController {

	private final WaterCheckService service;

	// MA water check screen: status of every water ANALOG signal (cached 30s)
	@GetMapping("/water-check")
	public ResponseEntity<WaterCheckResponseDto> waterCheck() {
		return ResponseEntity.ok(service.getWaterCheck());
	}
}
