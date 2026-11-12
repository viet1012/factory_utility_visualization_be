package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.repository.projection.LatestRecordView;
import com.example.factory_utility_visualization_be.model.F2UtilityParaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface F2UtilityParaHistoryRepo extends JpaRepository<F2UtilityParaHistory, Long> {

	@Query(value = """
    WITH filtered_params AS (
        SELECT
            sc.fac                    AS fac,
            ch.scada_id               AS scadaId,
            ch.cate                   AS cate,
            ch.box_id                 AS boxId,
            ch.box_device_id          AS boxDeviceId,

            p.plc_address             AS plcAddress,
            p.cate_id                 AS cateId,
            p.name_en                 AS nameEn,
            p.unit                    AS unit

        FROM f2_utility_scada_channel ch

        INNER JOIN f2_utility_scada sc
                ON sc.scada_id = ch.scada_id

        INNER JOIN f2_utility_para p
                ON p.box_device_id = ch.box_device_id

        WHERE
            (:facId IS NULL OR sc.fac = :facId)
            AND (:scadaId IS NULL OR ch.scada_id = :scadaId)
            AND (:cate IS NULL OR ch.cate = :cate)
            AND (:boxDeviceId IS NULL OR ch.box_device_id = :boxDeviceId)

            AND (
                :useDeviceIds = 0
                OR ch.box_device_id IN (:deviceIds)
            )

            AND (
                :useCateIds = 0
                OR p.cate_id IN (:cateIds)
            )
    )

    SELECT
        fp.boxDeviceId                AS boxDeviceId,
        fp.plcAddress                 AS plcAddress,

        latest.value                  AS value,
        latest.recorded_at            AS recordedAt,

        fp.cateId                     AS cateId,
        fp.nameEn                     AS nameEn,
        fp.unit                       AS unit,

        fp.scadaId                    AS scadaId,
        fp.fac                        AS fac,
        fp.cate                       AS cate,
        fp.boxId                      AS boxId

    FROM filtered_params fp

    OUTER APPLY (
        SELECT TOP (1)
            h.value,
            h.recorded_at
        FROM f2_utility_para_history h
        WHERE h.box_device_id = fp.boxDeviceId
          AND h.plc_address = fp.plcAddress
        ORDER BY
            h.recorded_at DESC,
            h.id DESC
    ) latest

    WHERE latest.recorded_at IS NOT NULL

    ORDER BY
        fp.boxDeviceId,
        fp.plcAddress
    """, nativeQuery = true)
	List<LatestRecordView> latestPerKey(
			@Param("facId") String facId,
			@Param("scadaId") String scadaId,
			@Param("cate") String cate,
			@Param("boxDeviceId") String boxDeviceId,

			@Param("useDeviceIds") int useDeviceIds,
			@Param("deviceIds") List<String> deviceIds,

			@Param("useCateIds") int useCateIds,
			@Param("cateIds") List<String> cateIds
	);
}

