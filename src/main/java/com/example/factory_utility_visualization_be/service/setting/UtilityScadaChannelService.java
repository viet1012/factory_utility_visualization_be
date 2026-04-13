package com.example.factory_utility_visualization_be.service.setting;

import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaChannelRepo;
import com.example.factory_utility_visualization_be.response.setting.BoxDto;
import com.example.factory_utility_visualization_be.response.setting.DeviceDto;
import com.example.factory_utility_visualization_be.response.setting.FacBoxDeviceProjection;
import com.example.factory_utility_visualization_be.response.setting.FacScadaBoxDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UtilityScadaChannelService {

	private final F2UtilityScadaChannelRepo repository;

	public UtilityScadaChannelService(F2UtilityScadaChannelRepo repository) {
		this.repository = repository;
	}

	public List<FacScadaBoxDto> getAllGroupedByFac() {
		List<FacBoxDeviceProjection> rows = repository.findAllFacBoxDevices();
		return groupRows(rows);
	}

	private List<FacScadaBoxDto> groupRows(List<FacBoxDeviceProjection> rows) {
		Map<String, FacScadaBoxDto> facMap = new LinkedHashMap<>();
		Map<String, BoxDto> boxMap = new LinkedHashMap<>();

		for (FacBoxDeviceProjection row : rows) {
			if (row.getFac() == null || row.getFac().isBlank()) {
				continue;
			}

			String facKey = row.getFac() + "::" + row.getScadaId();
			String boxKey = facKey + "::" + row.getBoxId();

			facMap.putIfAbsent(
					facKey,
					FacScadaBoxDto.builder()
							.fac(row.getFac())
							.scadaId(row.getScadaId())
							.boxes(new ArrayList<>())
							.build()
			);

			if (row.getBoxId() != null && !row.getBoxId().isBlank()) {
				boxMap.putIfAbsent(
						boxKey,
						BoxDto.builder()
								.boxId(row.getBoxId())
								.devices(new ArrayList<>())
								.build()
				);

				if (!facMap.get(facKey).getBoxes().contains(boxMap.get(boxKey))) {
					facMap.get(facKey).getBoxes().add(boxMap.get(boxKey));
				}

				if (row.getBoxDeviceId() != null && !row.getBoxDeviceId().isBlank()) {
					boxMap.get(boxKey).getDevices().add(
							DeviceDto.builder()
									.channelId(row.getChannelId())
									.cate(row.getCate())
									.boxDeviceId(row.getBoxDeviceId())
									.build()
					);
				}
			}
		}

		return new ArrayList<>(facMap.values());
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