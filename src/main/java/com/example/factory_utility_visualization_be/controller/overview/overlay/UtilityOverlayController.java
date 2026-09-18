package com.example.factory_utility_visualization_be.controller.overview.overlay;


import com.example.factory_utility_visualization_be.dto.overview.overlay.OverlayPosDto;
import com.example.factory_utility_visualization_be.service.overview.overlay.UtilityOverlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility/overlay")
@RequiredArgsConstructor
@CrossOrigin
public class UtilityOverlayController {

	private final UtilityOverlayService service;

	// GET layout theo fac
	@GetMapping
	public List<OverlayPosDto> getOverlay(
			@RequestParam String facId
	) {
		return service.getByFac(facId);
	}

	// UPSERT 1 record
	@PostMapping("/upsert")
	public OverlayPosDto upsert(
			@RequestBody OverlayPosDto dto
	) {
		return service.upsert(dto);
	}

	// Bulk save (optional)
	@PostMapping("/bulk")
	public List<OverlayPosDto> bulk(
			@RequestBody List<OverlayPosDto> list
	) {
		return list.stream()
				.map(service::upsert)
				.toList();
	}
}
