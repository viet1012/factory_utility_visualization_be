package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.UtilityOverlayPos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilityOverlayRepository
		extends JpaRepository<UtilityOverlayPos, Long> {

	List<UtilityOverlayPos> findByFacId(String facId);

	Optional<UtilityOverlayPos> findByFacIdAndBoxDeviceIdAndPlcAddress(
			String facId,
			String boxDeviceId,
			String plcAddress
	);
}