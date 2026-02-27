package com.example.factory_utility_visualization_be.controller.overview;

import com.example.factory_utility_visualization_be.dto.RangePreset;
import com.example.factory_utility_visualization_be.response.overview.FacTimeSeriesTreeResponse;
import com.example.factory_utility_visualization_be.service.overview.FacTimeSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/utility/chart")
public class FacTimeSeriesController {

	private final FacTimeSeriesService service;

	@GetMapping("/tree-series")
	public FacTimeSeriesTreeResponse getTreeSeries(
			@RequestParam String fac,
			@RequestParam(defaultValue = "TODAY") RangePreset range,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) String plcAddress
	) {
		return service.getFacTimeSeriesTree(fac, range, year, month, boxDeviceId, plcAddress);
	}
}