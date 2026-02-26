package com.example.factory_utility_visualization_be.controller;

import com.example.factory_utility_visualization_be.dto.RangePreset;
import com.example.factory_utility_visualization_be.response.UtilityTreeSeriesResponse;
import com.example.factory_utility_visualization_be.service.FacSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/utility/chart")
public class FacSeriesController {

	private final FacSeriesService service;

	@GetMapping("/tree-series")
	public UtilityTreeSeriesResponse getTreeSeries(
			@RequestParam String fac,
			@RequestParam(defaultValue = "TODAY") RangePreset range,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) String plcAddress
	) {
		return service.getByFac(fac, range, year, month, boxDeviceId, plcAddress);
	}
}