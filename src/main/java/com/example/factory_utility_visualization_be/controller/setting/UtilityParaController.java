package com.example.factory_utility_visualization_be.controller.setting;


import com.example.factory_utility_visualization_be.request.setting.UtilityParaRequest;
import com.example.factory_utility_visualization_be.response.setting.UtilityParaResponse;
import com.example.factory_utility_visualization_be.service.setting.UtilityParaService;
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

	@GetMapping
	public List<UtilityParaResponse> getAll() {
		return service.getAll();
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

	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	// ===== FILTER =====

	@GetMapping("/box/{boxDeviceId}")
	public List<UtilityParaResponse> getByBox(@PathVariable String boxDeviceId) {
		return service.getByBoxDeviceId(boxDeviceId);
	}

	@GetMapping("/cate/{cateId}")
	public List<UtilityParaResponse> getByCate(@PathVariable String cateId) {
		return service.getByCateId(cateId);
	}

	@GetMapping("/important/{flag}")
	public List<UtilityParaResponse> getByImportant(@PathVariable Integer flag) {
		return service.getByImportant(flag);
	}

	@GetMapping("/alert/{flag}")
	public List<UtilityParaResponse> getByAlert(@PathVariable Integer  flag) {
		return service.getByAlert(flag);
	}
}
