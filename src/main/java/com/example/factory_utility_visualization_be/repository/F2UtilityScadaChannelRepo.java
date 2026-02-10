package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface F2UtilityScadaChannelRepo extends JpaRepository<F2UtilityScadaChannel, Long> {

    List<F2UtilityScadaChannel> findByScadaId(String scadaId);

    List<F2UtilityScadaChannel> findByCate(String cate);

    List<F2UtilityScadaChannel> findByScadaIdAndCate(String scadaId, String cate);

    List<F2UtilityScadaChannel> findByBoxDeviceId(String boxDeviceId);
}
