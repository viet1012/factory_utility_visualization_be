package com.example.factory_utility_visualization_be.controller.runtime;

import com.example.factory_utility_visualization_be.dto.latest.LatestFacilityDto;
import com.example.factory_utility_visualization_be.service.runtime.UtilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityController {

	private final UtilityQueryService service;

	// Latest theo device/param (nhiều param một lần)

	@GetMapping("/latest")
	public ResponseEntity<List<LatestFacilityDto>> getLatest(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) List<String> cateIds
	) {
		return ResponseEntity.ok(
				service.getLatest(
						facId,
						scadaId,
						cate,
						boxDeviceId,
						cateIds
				)
		);
	}
}
