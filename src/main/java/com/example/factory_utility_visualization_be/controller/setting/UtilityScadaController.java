package com.example.factory_utility_visualization_be.controller.setting;


import com.example.factory_utility_visualization_be.request.setting.UtilityScadaRequest;
import com.example.factory_utility_visualization_be.response.setting.UtilityScadaResponse;
import com.example.factory_utility_visualization_be.service.setting.UtilityScadaService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/utility-scada")
@CrossOrigin(origins = "*")
public class UtilityScadaController {

	private final UtilityScadaService service;

	public UtilityScadaController(UtilityScadaService service) {
		this.service = service;
	}

	@GetMapping
	public List<UtilityScadaResponse> getAll() {
		return service.getAll();
	}

	@GetMapping("/{id}")
	public UtilityScadaResponse getById(@PathVariable Long id) {
		return service.getById(id);
	}

	@GetMapping("/scada/{scadaId}")
	public UtilityScadaResponse getByScadaId(@PathVariable String scadaId) {
		return service.getByScadaId(scadaId);
	}

	@GetMapping("/fac/{fac}")
	public List<UtilityScadaResponse> getByFac(@PathVariable String fac) {
		return service.getByFac(fac);
	}


	@GetMapping("/alert/{alert}")
	public List<UtilityScadaResponse> getByAlert(@PathVariable Boolean alert) {
		return service.getByAlert(alert);
	}

	@PostMapping
	public UtilityScadaResponse create(@RequestBody UtilityScadaRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public UtilityScadaResponse update(
			@PathVariable Long id,
			@RequestBody UtilityScadaRequest request
	) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}
}