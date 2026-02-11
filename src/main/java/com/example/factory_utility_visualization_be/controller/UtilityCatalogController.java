package com.example.factory_utility_visualization_be.controller;

import com.example.factory_utility_visualization_be.dto.UtilityCatalogDto;
import com.example.factory_utility_visualization_be.service.UtilityCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityCatalogController {

	private final UtilityCatalogService service;

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
