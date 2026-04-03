package com.example.factory_utility_visualization_be.controller.setting;

import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.service.setting.UtilityScadaChannelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility-scada-channels")
public class UtilityScadaChannelController {

	private final UtilityScadaChannelService service;

	public UtilityScadaChannelController(UtilityScadaChannelService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<List<F2UtilityScadaChannel>> getAll() {
		return ResponseEntity.ok(service.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<F2UtilityScadaChannel> getById(@PathVariable Long id) {
		return ResponseEntity.ok(service.findById(id));
	}

	@PostMapping
	public ResponseEntity<F2UtilityScadaChannel> create(@RequestBody F2UtilityScadaChannel request) {
		return ResponseEntity.ok(service.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<F2UtilityScadaChannel> update(
			@PathVariable Long id,
			@RequestBody F2UtilityScadaChannel request
	) {
		return ResponseEntity.ok(service.update(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<String> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.ok("Deleted successfully");
	}
}