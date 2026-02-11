package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.dto.mapper.LatestRecordView;
import com.example.factory_utility_visualization_be.dto.mapper.MinutePointView;
import com.example.factory_utility_visualization_be.model.F2UtilityParaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface F2UtilityParaHistoryRepo extends JpaRepository<F2UtilityParaHistory, Long> {

    Optional<F2UtilityParaHistory> findTopByBoxDeviceIdAndPlcAddressOrderByRecordedAtDesc(
            String boxDeviceId, String plcAddress
    );

    // Latest cho nhiều param: max(recorded_at) group by (box_device_id, plc_address)
    @Query("""
              select h from F2UtilityParaHistory h
              where h.recordedAt = (
                select max(h2.recordedAt) from F2UtilityParaHistory h2
                where h2.boxDeviceId = h.boxDeviceId and h2.plcAddress = h.plcAddress
              )
                and (:boxDeviceId is null or h.boxDeviceId = :boxDeviceId)
                and (:boxDeviceIds is null or h.boxDeviceId in :boxDeviceIds)
  """)
    List<F2UtilityParaHistory> latestPerKey(
            @Param("boxDeviceId") String boxDeviceId,
            @Param("boxDeviceIds") List<String> boxDeviceIds
    );

    @Query("""
              select h from F2UtilityParaHistory h
              where h.boxDeviceId = :boxDeviceId
                and h.plcAddress = :plcAddress
                and h.recordedAt between :from and :to
              order by h.recordedAt asc
  """)
    List<F2UtilityParaHistory> seriesRaw(
            @Param("boxDeviceId") String boxDeviceId,
            @Param("plcAddress") String plcAddress,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            WITH x AS (
                SELECT
                    h.box_device_id     AS boxDeviceId,
                    h.plc_address       AS plcAddress,
                    h.value             AS value,
                    h.recorded_at       AS recordedAt,
                    p.cate_id           AS cateId,
                    ch.scada_id         AS scadaId,
                    sc.fac              AS fac,
                    ch.cate             AS cate,
                    ch.box_id           AS boxId,
            
                    ROW_NUMBER() OVER (
                        PARTITION BY h.box_device_id, h.plc_address
                        ORDER BY h.recorded_at DESC, h.id DESC
                    ) AS rn
                FROM f2_utility_para_history h
                LEFT JOIN f2_utility_scada_channel ch
                       ON ch.box_device_id = h.box_device_id
                LEFT JOIN f2_utility_scada sc
                       ON sc.scada_id = ch.scada_id
                LEFT JOIN f2_utility_para p
                       ON p.box_device_id = h.box_device_id
                      AND p.plc_address   = h.plc_address
                WHERE
                    (:boxDeviceId IS NULL OR h.box_device_id = :boxDeviceId)
                    AND (:facId   IS NULL OR sc.fac = :facId)
                    AND (:scadaId IS NULL OR ch.scada_id = :scadaId)
                    AND (:cate    IS NULL OR ch.cate = :cate)
            
                    -- ✅ list filter bật/tắt bằng flag
                    AND (:useDeviceIds = 0 OR h.box_device_id IN (:deviceIds))
                    AND (:useCateIds   = 0 OR p.cate_id IN (:cateIds))
            )
            SELECT
                boxDeviceId, plcAddress, value, recordedAt,
                cateId, scadaId, fac, cate, boxId
            FROM x
            WHERE rn = 1
            ORDER BY boxDeviceId, plcAddress
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

    @Query(value = """
    SELECT
        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) AS ts,
        AVG(CAST(h.value AS decimal(18,6)))                   AS value,
        h.box_device_id                                       AS boxDeviceId,
        h.plc_address                                         AS plcAddress,
        p.cate_id                                             AS cateId
    FROM f2_utility_para_history h
    LEFT JOIN f2_utility_para p
           ON p.box_device_id = h.box_device_id
          AND p.plc_address   = h.plc_address
    WHERE
        h.recorded_at >= :fromTs
        AND h.recorded_at <  :toTs
        AND (:boxDeviceId IS NULL OR h.box_device_id = :boxDeviceId)
        AND (:plcAddress  IS NULL OR h.plc_address   = :plcAddress)
        AND (:useCateIds  = 0 OR p.cate_id IN (:cateIds))
    GROUP BY
        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0),
        h.box_device_id,
        h.plc_address,
        p.cate_id
    ORDER BY ts
    """, nativeQuery = true)
    List<MinutePointView> seriesByMinuteAvg(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("boxDeviceId") String boxDeviceId,
            @Param("plcAddress") String plcAddress,
            @Param("useCateIds") int useCateIds,
            @Param("cateIds") List<String> cateIds
    );

    @Query(value = """
    WITH t AS (
        SELECT
            DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) AS ts,
            h.box_device_id AS boxDeviceId,
            h.plc_address   AS plcAddress,
            p.cate_id       AS cateId,
            h.value         AS value,
            ROW_NUMBER() OVER (
                PARTITION BY
                    h.box_device_id,
                    h.plc_address,
                    DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0)
                ORDER BY h.recorded_at DESC, h.id DESC
            ) rn
        FROM f2_utility_para_history h
        LEFT JOIN f2_utility_para p
               ON p.box_device_id = h.box_device_id
              AND p.plc_address   = h.plc_address
        WHERE
            h.recorded_at >= :fromTs
            AND h.recorded_at <  :toTs
            AND (:boxDeviceId IS NULL OR h.box_device_id = :boxDeviceId)
            AND (:plcAddress  IS NULL OR h.plc_address   = :plcAddress)
            AND (:useCateIds  = 0 OR p.cate_id IN (:cateIds))
    )
    SELECT ts, value, boxDeviceId, plcAddress, cateId
    FROM t
    WHERE rn = 1
    ORDER BY ts
    """, nativeQuery = true)
    List<MinutePointView> seriesByMinuteLast(
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs,
            @Param("boxDeviceId") String boxDeviceId,
            @Param("plcAddress") String plcAddress,
            @Param("useCateIds") int useCateIds,
            @Param("cateIds") List<String> cateIds
    );

}

