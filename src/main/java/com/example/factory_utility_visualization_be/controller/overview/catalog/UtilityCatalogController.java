package com.example.factory_utility_visualization_be.controller.overview.catalog;

import com.example.factory_utility_visualization_be.dto.UtilityCatalogDto;
import com.example.factory_utility_visualization_be.dto.overview.catalog.UtilityChartCatalogResponse;
import com.example.factory_utility_visualization_be.service.UtilityCatalogService;
import com.example.factory_utility_visualization_be.service.catalog.UtilityChartCatalogItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityCatalogController {

	private final UtilityCatalogService service;

	private final UtilityChartCatalogItemService utilityCatalogService;
	@GetMapping("/chart-catalog")
	public UtilityChartCatalogResponse chartCatalog(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String boxId,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(defaultValue = "0") Integer importantOnly
	) {
		return utilityCatalogService.getChartCatalog(
				facId,
				cate,
				scadaId,
				boxId,
				boxDeviceId,
				importantOnly
		);
	}

	@GetMapping("/catalog")
	public UtilityCatalogDto catalog(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) Boolean importantOnly,
			@RequestParam(required = false) String include
	) {
		return service.getCatalog(
				facId,
				scadaId,
				cate,
				boxDeviceId,
				importantOnly,
				include
		);
	}
}
