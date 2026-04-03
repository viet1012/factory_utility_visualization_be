package com.example.factory_utility_visualization_be.service.setting;

import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaChannelRepo;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UtilityScadaChannelService {

	private final F2UtilityScadaChannelRepo repository;

	public UtilityScadaChannelService(F2UtilityScadaChannelRepo repository) {
		this.repository = repository;
	}

	public List<F2UtilityScadaChannel> findAll() {
		return repository.findAll();
	}

	public F2UtilityScadaChannel findById(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new RuntimeException("Channel not found with id: " + id));
	}

	public F2UtilityScadaChannel create(F2UtilityScadaChannel request) {
		return repository.save(request);
	}

	public F2UtilityScadaChannel update(Long id, F2UtilityScadaChannel request) {
		F2UtilityScadaChannel entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("Channel not found with id: " + id));

		entity.setScadaId(request.getScadaId());
		entity.setCate(request.getCate());
		entity.setBoxDeviceId(request.getBoxDeviceId());
		entity.setBoxId(request.getBoxId());

		return repository.save(entity);
	}

	public void delete(Long id) {
		F2UtilityScadaChannel entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("Channel not found with id: " + id));
		repository.delete(entity);
	}
}