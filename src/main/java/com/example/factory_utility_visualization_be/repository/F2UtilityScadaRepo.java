package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.model.F2UtilityScada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface F2UtilityScadaRepo extends JpaRepository<F2UtilityScada, Long> {
	Optional<F2UtilityScada> findByScadaId(String scadaId);

	List<F2UtilityScada> findByFac(String fac);

	List<F2UtilityScada> findByConnected(String connected);

	List<F2UtilityScada> findByAlert(Boolean alert);
}
