package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import com.example.factory_utility_visualization_be.response.setting.para.FacBoxDeviceParaProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface F2UtilityParaRepo extends JpaRepository<F2UtilityPara, Long> {

    List<F2UtilityPara> findByBoxDeviceId(String boxDeviceId);

    List<F2UtilityPara> findByCateId(String cateId);

    List<F2UtilityPara> findByIsImportant(Integer isImportant);

    List<F2UtilityPara> findByIsAlert(Integer isAlert);

    @Query("""
        select p
        from F2UtilityPara p
        where (:boxDeviceId is null or p.boxDeviceId = :boxDeviceId)
          and (:importantOnly = 0 or p.isImportant = 1)
          and exists (
                select 1
                from F2UtilityScadaChannel c, F2UtilityScada s
                where c.boxDeviceId = p.boxDeviceId
                  and s.scadaId = c.scadaId
                  and (:cate is null or c.cate = :cate)
                  and (:scadaId is null or s.scadaId = :scadaId)
                  and (:facId is null or s.fac = :facId)
          )
    """)
    List<F2UtilityPara> searchParams(
            @Param("boxDeviceId") String boxDeviceId,
            @Param("cate") String cate,
            @Param("scadaId") String scadaId,
            @Param("facId") String facId,
            @Param("importantOnly") int importantOnly
    );

    List<F2UtilityPara> findByCateIdIn(List<String> cateIds);


    @Query(value = """
    SELECT
        s.fac AS fac,
        s.scada_id AS scadaId,
        c.id AS channelId,
        c.cate AS cate,
        c.box_id AS boxId,
        c.box_device_id AS boxDeviceId,

        p.id AS paraId,
        p.plc_address AS plcAddress,
        p.value_type AS valueType,
        p.unit AS unit,
        p.cate_id AS cateId,
        p.name_vi AS nameVi,
        p.name_en AS nameEn,
        p.is_important AS isImportant,
        p.is_alert AS isAlert,
        p.min_alert AS minAlert,
        p.max_alert AS maxAlert
    FROM f2_utility_scada s
    LEFT JOIN f2_utility_scada_channel c
        ON s.scada_id = c.scada_id
    LEFT JOIN f2_utility_para p
        ON c.box_device_id = p.box_device_id
    ORDER BY s.fac, s.scada_id, c.box_id, c.box_device_id, p.plc_address
    """, nativeQuery = true)
    List<FacBoxDeviceParaProjection> findAllFacBoxDeviceParas();
}