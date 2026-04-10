package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.service.setting.FacBoxDeviceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface F2UtilityScadaChannelRepo extends JpaRepository<F2UtilityScadaChannel, Long> {

    List<F2UtilityScadaChannel> findByScadaId(String scadaId);

    List<F2UtilityScadaChannel> findByCate(String cate);

    List<F2UtilityScadaChannel> findByScadaIdAndCate(String scadaId, String cate);

    List<F2UtilityScadaChannel> findByBoxDeviceId(String boxDeviceId);

    @Query(value = """
        SELECT
            s.fac AS fac,
            s.scada_id AS scadaId,
            c.id AS channelId,
            c.cate AS cate,
            c.box_id AS boxId,
            c.box_device_id AS boxDeviceId
        FROM f2_utility_scada s
        LEFT JOIN f2_utility_scada_channel c
            ON s.scada_id = c.scada_id

        """, nativeQuery = true)
    List<FacBoxDeviceProjection> findAllFacBoxDevices();
}
