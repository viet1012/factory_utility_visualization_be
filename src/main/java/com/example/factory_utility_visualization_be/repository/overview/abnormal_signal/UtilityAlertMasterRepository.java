package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal;

import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UtilityAlertMasterRepository extends JpaRepository<F2UtilityAlertMaster, Long> {

	List<F2UtilityAlertMaster> findByIsActiveTrueOrderByIdAsc();
}
