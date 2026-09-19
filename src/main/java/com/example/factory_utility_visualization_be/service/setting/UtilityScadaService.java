package com.example.factory_utility_visualization_be.service.setting;


import com.example.factory_utility_visualization_be.model.F2UtilityScada;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaRepo;
import com.example.factory_utility_visualization_be.request.setting.UtilityScadaRequest;
import com.example.factory_utility_visualization_be.response.setting.UtilityScadaResponse;
import com.example.factory_utility_visualization_be.service.runtime.UtilityMasterDataCacheService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UtilityScadaService {

	private final F2UtilityScadaRepo repository;
	private final UtilityMasterDataCacheService masterDataCache;

	public UtilityScadaService(F2UtilityScadaRepo repository, UtilityMasterDataCacheService masterDataCache) {
		this.repository = repository;
		this.masterDataCache = masterDataCache;
	}

	public List<UtilityScadaResponse> getAll() {
		return repository.findAll().stream()
				.map(this::toResponse)
				.toList();
	}

	public UtilityScadaResponse getById(Long id) {
		F2UtilityScada entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityScada not found with id: " + id));
		return toResponse(entity);
	}

	public UtilityScadaResponse create(UtilityScadaRequest request) {
		F2UtilityScada entity = new F2UtilityScada();
		mapRequestToEntity(request, entity);

		if (entity.getTimeUpdate() == null) {
			entity.setTimeUpdate(LocalDateTime.now());
		}

		UtilityScadaResponse response = toResponse(repository.save(entity));
		masterDataCache.evictScadas();
		return response;
	}

	public UtilityScadaResponse update(Long id, UtilityScadaRequest request) {
		F2UtilityScada entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityScada not found with id: " + id));

		mapRequestToEntity(request, entity);

		if (entity.getTimeUpdate() == null) {
			entity.setTimeUpdate(LocalDateTime.now());
		}

		UtilityScadaResponse response = toResponse(repository.save(entity));
		masterDataCache.evictScadas();
		return response;
	}

	public void delete(Long id) {
		F2UtilityScada entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityScada not found with id: " + id));
		repository.delete(entity);
		masterDataCache.evictScadas();
	}

	public UtilityScadaResponse getByScadaId(String scadaId) {
		F2UtilityScada entity = repository.findByScadaId(scadaId)
				.orElseThrow(() -> new RuntimeException("UtilityScada not found with scadaId: " + scadaId));
		return toResponse(entity);
	}

	public List<UtilityScadaResponse> getByFac(String fac) {
		return repository.findByFac(fac).stream()
				.map(this::toResponse)
				.toList();
	}



	public List<UtilityScadaResponse> getByAlert(Boolean alert) {
		return repository.findByAlert(alert).stream()
				.map(this::toResponse)
				.toList();
	}

	private void mapRequestToEntity(UtilityScadaRequest request, F2UtilityScada entity) {
		entity.setScadaId(request.getScadaId());
		entity.setFac(request.getFac());
		entity.setPlcIp(request.getPlcIp());
		entity.setPlcPort(request.getPlcPort());
		entity.setPcName(request.getPcName());
		entity.setWlan(request.getWlan());
		entity.setConnected(request.getConnected());
		entity.setAlert(request.getAlert());
		entity.setTimeUpdate(request.getTimeUpdate());
	}

	private UtilityScadaResponse toResponse(F2UtilityScada entity) {
		UtilityScadaResponse response = new UtilityScadaResponse();
		response.setId(entity.getId());
		response.setScadaId(entity.getScadaId());
		response.setFac(entity.getFac());
		response.setPlcIp(entity.getPlcIp());
		response.setPlcPort(entity.getPlcPort());
		response.setPcName(entity.getPcName());
		response.setWlan(entity.getWlan());
		response.setConnected(entity.getConnected());
		response.setAlert(entity.getAlert());
		response.setTimeUpdate(entity.getTimeUpdate());
		return response;
	}
}