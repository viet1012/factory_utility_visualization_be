package com.example.factory_utility_visualization_be.controller.setting;


import com.example.factory_utility_visualization_be.request.setting.UtilityParaRequest;
import com.example.factory_utility_visualization_be.response.setting.UtilityParaResponse;
import com.example.factory_utility_visualization_be.response.setting.para.FacScadaBoxParaDto;
import com.example.factory_utility_visualization_be.service.setting.UtilityParaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/utility-para")
@CrossOrigin
public class UtilityParaController {

	private final UtilityParaService service;

	public UtilityParaController(UtilityParaService service) {
		this.service = service;
	}

	@GetMapping("/grouped-by-fac-with-paras")
	public ResponseEntity<List<FacScadaBoxParaDto>> getAllGroupedByFacWithParas() {
		return ResponseEntity.ok(service.getAllGroupedByFacWithParas());
	}

	@GetMapping("/{id}")
	public UtilityParaResponse getById(@PathVariable Long id) {
		return service.getById(id);
	}

	@PostMapping
	public UtilityParaResponse create(@RequestBody UtilityParaRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public UtilityParaResponse update(@PathVariable Long id,
	                                  @RequestBody UtilityParaRequest request) {
		return service.update(id, request);
	}
}
