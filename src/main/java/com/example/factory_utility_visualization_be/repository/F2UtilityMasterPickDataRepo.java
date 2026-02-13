package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.F2UtilityMasterPickData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface F2UtilityMasterPickDataRepo extends JpaRepository<F2UtilityMasterPickData, Long> {

	List<F2UtilityMasterPickData> findByCateId(String cateId);
}
