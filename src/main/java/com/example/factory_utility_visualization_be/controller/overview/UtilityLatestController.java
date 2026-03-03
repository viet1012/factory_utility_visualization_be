package com.example.factory_utility_visualization_be.controller.overview;

import com.example.factory_utility_visualization_be.request.overview.LatestRequest;
import com.example.factory_utility_visualization_be.response.overview.FacTimeSeriesTreeResponse;
import com.example.factory_utility_visualization_be.service.overview.UtilityLatestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityLatestController {

	@Autowired
	private UtilityLatestService service;

	@GetMapping("/latest-tree")
	public FacTimeSeriesTreeResponse latestTree(
			@RequestParam(required = false) List<String> facIds,
			@RequestParam(required = false) List<String> boxDeviceIds,
			@RequestParam(required = false) List<String> plcAddresses,
			@RequestParam(required = false) List<String> cateIds
	) {
		return service.getLatestTree(facIds, boxDeviceIds, plcAddresses, cateIds);
	}
}
