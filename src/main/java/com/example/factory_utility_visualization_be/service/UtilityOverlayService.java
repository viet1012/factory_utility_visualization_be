package com.example.factory_utility_visualization_be.service;
import com.example.factory_utility_visualization_be.dto.OverlayPosDto;
import com.example.factory_utility_visualization_be.model.UtilityOverlayPos;
import com.example.factory_utility_visualization_be.repository.UtilityOverlayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilityOverlayService {

	private final UtilityOverlayRepository repo;

	public List<OverlayPosDto> getByFac(String facId) {
		return repo.findByFacId(facId)
				.stream()
				.map(this::toDto)
				.collect(Collectors.toList());
	}

	public OverlayPosDto upsert(OverlayPosDto dto) {

		UtilityOverlayPos entity = repo
				.findByFacIdAndBoxDeviceIdAndPlcAddress(
						dto.getFacId(),
						dto.getBoxDeviceId(),
						dto.getPlcAddress()
				)
				.orElse(new UtilityOverlayPos());

		entity.setFacId(dto.getFacId());
		entity.setBoxDeviceId(dto.getBoxDeviceId());
		entity.setPlcAddress(dto.getPlcAddress());
		entity.setX(dto.getX());
		entity.setY(dto.getY());
		entity.setUpdatedAt(LocalDateTime.now());

		repo.save(entity);

		return toDto(entity);
	}

	private OverlayPosDto toDto(UtilityOverlayPos e) {
		OverlayPosDto d = new OverlayPosDto();
		d.setFacId(e.getFacId());
		d.setBoxDeviceId(e.getBoxDeviceId());
		d.setPlcAddress(e.getPlcAddress());
		d.setX(e.getX());
		d.setY(e.getY());
		return d;
	}
}
