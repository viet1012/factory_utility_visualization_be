package com.example.factory_utility_visualization_be.controller.setting;

import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.service.setting.UtilityScadaChannelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/utility-scada-channels")
public class UtilityScadaChannelController {

	private final UtilityScadaChannelService service;

	public UtilityScadaChannelController(UtilityScadaChannelService service) {
		this.service = service;
	}

	// GET ALL
	@GetMapping
	public ResponseEntity<List<F2UtilityScadaChannel>> getAll() {
		return ResponseEntity.ok(service.findAll());
	}

	// GET BY ID
	@GetMapping("/{id}")
	public ResponseEntity<F2UtilityScadaChannel> getById(@PathVariable Long id) {
		F2UtilityScadaChannel data = service.findById(id);
		return ResponseEntity.ok(data);
	}

	// CREATE
	@PostMapping
	public ResponseEntity<F2UtilityScadaChannel> create(
			@RequestBody F2UtilityScadaChannel request
	) {
		F2UtilityScadaChannel created = service.create(request);
		return ResponseEntity.status(201).body(created); // 👈 chuẩn REST
	}

	// UPDATE
	@PutMapping("/{id}")
	public ResponseEntity<F2UtilityScadaChannel> update(
			@PathVariable Long id,
			@RequestBody F2UtilityScadaChannel request
	) {
		return ResponseEntity.ok(service.update(id, request));
	}

	// DELETE
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build(); // 👈 chuẩn REST
	}
}