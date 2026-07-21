package com.example.factory_utility_visualization_be.repository;


import com.example.factory_utility_visualization_be.dto.mapper.HourPointView;
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

	@Query(value = """
    SELECT *
    FROM dbo.f2_utility_para_history
    WHERE box_device_id = :boxDeviceId
      AND recorded_at >= :from
      AND recorded_at <= :to
      AND DATEPART(HOUR, recorded_at) IN (:hours)
      AND DATEPART(MINUTE, recorded_at) BETWEEN 0 AND :minuteWindow
    ORDER BY recorded_at ASC, plc_address ASC
    """, nativeQuery = true)
	List<F2UtilityParaHistory> findByBoxDeviceIdAndDateRangeAndHours(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("hours") List<Integer> hours,
			@Param("minuteWindow") int minuteWindow
	);

	Optional<F2UtilityParaHistory> findTopByBoxDeviceIdAndPlcAddressOrderByRecordedAtDesc(
			String boxDeviceId, String plcAddress
	);



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
            p.unit                    AS unit,
            ISNULL(p.is_alert, 0)      AS isAlert,
            p.min_alert               AS minVolStd,
            p.max_alert               AS maxVolStd

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
        fp.boxId                      AS boxId,

        alarmData.minVol              AS minVol,
        alarmData.maxVol              AS maxVol,

        fp.minVolStd                  AS minVolStd,
        fp.maxVolStd                  AS maxVolStd,

        CASE
            WHEN fp.isAlert <> 1 THEN 'Normal'

            WHEN alarmData.minVol IS NULL
              OR alarmData.maxVol IS NULL
            THEN 'Normal'

            WHEN fp.minVolStd IS NOT NULL
             AND alarmData.minVol < fp.minVolStd
            THEN 'Alarm'

            WHEN fp.maxVolStd IS NOT NULL
             AND alarmData.maxVol > fp.maxVolStd
            THEN 'Alarm'

            ELSE 'Normal'
        END                           AS alarm

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

    OUTER APPLY (
        SELECT
            MIN(h.value) AS minVol,
            MAX(h.value) AS maxVol
        FROM f2_utility_para_history h
        WHERE fp.isAlert = 1
          AND h.box_device_id = fp.boxDeviceId
          AND h.plc_address = fp.plcAddress
          AND h.recorded_at >= DATEADD(DAY, -1, GETDATE())
          AND h.value <> 0
    ) alarmData

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


	@Query(value = """
			WITH t AS (
			    SELECT
			        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) AS ts,
			        h.box_device_id AS boxDeviceId,
			        h.plc_address   AS plcAddress,
			        p.cate_id       AS cateId,
			        p.name_en       AS nameEn,
			        p.name_vi       AS nameVi,
			        p.unit          AS unit,      -- ✅ NEW
			        h.value         AS value,
			        s.fac           AS fac,
			        ch.cate         AS cate,
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
			    LEFT JOIN f2_utility_scada_channel ch
			           ON ch.box_device_id = h.box_device_id
			    LEFT JOIN f2_utility_scada s
			           ON s.scada_id = ch.scada_id
			    WHERE
			        h.recorded_at >= :fromTs
			        AND h.recorded_at <  :toTs
			        AND (:boxDeviceId IS NULL OR h.box_device_id = :boxDeviceId)
			        AND (:plcAddress  IS NULL OR h.plc_address   = :plcAddress)
			        AND (:fac IS NULL OR s.fac = :fac)
			        AND (:cate IS NULL OR ch.cate = :cate)
			        AND (:useCateIds = 0 OR p.cate_id IN (:cateIds))
			)
			SELECT ts, value, boxDeviceId, plcAddress, cateId, nameEn, nameVi, unit, fac, cate
			FROM t
			WHERE rn = 1
			ORDER BY ts
			""", nativeQuery = true)
	List<MinutePointView> seriesByMinuteLast(
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddress") String plcAddress,
			@Param("fac") String fac,
			@Param("cate") String cate,
			@Param("useCateIds") int useCateIds,
			@Param("cateIds") List<String> cateIds
	);




	@Query(value = """
			WITH t AS (
			    SELECT
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, h.recorded_at), 0) AS ts,
			        h.box_device_id AS boxDeviceId,
			        h.plc_address   AS plcAddress,
			        p.cate_id       AS cateId,
			        p.name_en       AS nameEn,
			        p.name_vi       AS nameVi,
			        CAST(h.value AS decimal(18,6)) AS value,
			        s.fac           AS fac,
			        ch.cate         AS cate
			    FROM f2_utility_para_history h
			    LEFT JOIN f2_utility_para p
			           ON p.box_device_id = h.box_device_id
			          AND p.plc_address   = h.plc_address
			    LEFT JOIN f2_utility_scada_channel ch
			           ON ch.box_device_id = h.box_device_id
			    LEFT JOIN f2_utility_scada s
			           ON s.scada_id = ch.scada_id
			    WHERE
			        h.recorded_at >= :fromTs
			        AND h.recorded_at <  :toTs
			        AND (:plcAddress IS NULL OR h.plc_address = :plcAddress)
			        AND (:boxDeviceId IS NULL OR h.box_device_id = :boxDeviceId)
			        AND (:fac IS NULL OR s.fac = :fac)
			        AND (:cate IS NULL OR ch.cate = :cate)
			        AND (:scadaId IS NULL OR ch.scada_id = :scadaId)
			        AND (:useCateIds = 0 OR p.cate_id IN (:cateIds))
			)
			SELECT
			    ts,
			    COALESCE(SUM(value),0) AS value,
			    MAX(boxDeviceId) AS boxDeviceId,
			    MAX(plcAddress)  AS plcAddress,
			    MAX(cateId)      AS cateId,
			    MAX(nameEn)      AS nameEn,
			    MAX(nameVi)      AS nameVi,
			    MAX(fac)         AS fac,
			    MAX(cate)        AS cate
			FROM t
			GROUP BY ts
			ORDER BY ts
			""", nativeQuery = true)
	List<HourPointView> seriesByHourSum(
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,

			@Param("plcAddress") String plcAddress,
			@Param("boxDeviceId") String boxDeviceId,

			@Param("fac") String fac,
			@Param("cate") String cate,
			@Param("scadaId") String scadaId,

			@Param("useCateIds") int useCateIds,
			@Param("cateIds") List<String> cateIds
	);
}

