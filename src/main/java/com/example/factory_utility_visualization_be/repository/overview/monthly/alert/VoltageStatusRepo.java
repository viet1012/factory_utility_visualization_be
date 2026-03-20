package com.example.factory_utility_visualization_be.repository.overview.monthly.alert;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface VoltageStatusRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        SELECT
            'Voltage' as name,
            MIN(value) as minVol,
            MAX(value) as maxVol,
            CASE
                WHEN MIN(value) < 205 OR MAX(value) > 245
                THEN 'Alarm'
                ELSE 'Normal'
            END as alarm
        FROM F2_Utility_Para_History
        WHERE plc_address IN ('D12','D14','D16')
        AND value <> 0
        AND recorded_at > DATEADD(DAY,-1,GETDATE())
        """, nativeQuery = true)
	List<Object[]> getVoltageStatus();
//@Query(value = """
//    SELECT
//        'Voltage' as name,
//        COALESCE(MIN(hi.value), 0) as minVol,
//        COALESCE(MAX(hi.value), 0) as maxVol,
//        CASE
//            WHEN COALESCE(MIN(hi.value), 0) < 205
//              OR COALESCE(MAX(hi.value), 0) > 245
//            THEN 'Alarm'
//            ELSE 'Normal'
//        END as alarm
//    FROM F2_Utility_Para_History hi
//
//    INNER JOIN F2_Utility_Para pa
//        ON hi.box_device_id = pa.box_device_id
//       AND hi.plc_address  = pa.plc_address
//
//    INNER JOIN F2_Utility_Scada_Channel ch
//        ON hi.box_device_id = ch.box_device_id
//
//    INNER JOIN F2_Utility_Scada sc
//        ON ch.scada_id = sc.scada_id
//
//    WHERE pa.cate_id = 'E_Vol'   -- 🔥 FILTER THEO CATEGORY
//
//      AND hi.value <> 0
//      AND hi.recorded_at > DATEADD(DAY, -1, GETDATE())
//
//      AND (:fac = 'KVH' OR sc.fac = :fac)
//    """, nativeQuery = true)
//List<Object[]> getVoltageStatus(@Param("fac") String fac);

	@Query(value = """

			SELECT
	          DATEADD(MINUTE, DATEDIFF(MINUTE, 0, recorded_at), 0) as recorded_minute,
	             MAX(CASE WHEN plc_address='D12' THEN [value] END) AS D12,
	             MAX(CASE WHEN plc_address='D14' THEN [value] END) AS D14,
	             MAX(CASE WHEN plc_address='D16' THEN [value] END) AS D16,

	             CASE
	                 WHEN MAX(CASE WHEN plc_address='D12' THEN [value] END) NOT BETWEEN 205 AND 245
	                   OR MAX(CASE WHEN plc_address='D14' THEN [value] END) NOT BETWEEN 205 AND 245
	                   OR MAX(CASE WHEN plc_address='D16' THEN [value] END) NOT BETWEEN 205 AND 245
	                 THEN 'Alarm'
	                 ELSE 'Normal'
	             END AS Alarm
	         FROM F2_Utility_Para_History
	         WHERE plc_address IN ('D12','D14','D16')
	         AND recorded_at > DATEADD(DAY,-1,GETDATE())
	         AND [value] <> 0
	         GROUP BY DATEADD(MINUTE, DATEDIFF(MINUTE, 0, recorded_at), 0)
	         ORDER BY DATEADD(MINUTE, DATEDIFF(MINUTE, 0, recorded_at), 0);

        """, nativeQuery = true)
	List<Map<String,Object>> getVoltageDetail();

	@Query(value = """
    WITH voltage_data AS (
        SELECT
            DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0) as recorded_minute,
            pa.name_en AS name,   -- 🔥 FIX Ở ĐÂY
            hi.value
        FROM F2_Utility_Para_History hi

        INNER JOIN F2_Utility_Para pa
            ON hi.box_device_id = pa.box_device_id
           AND hi.plc_address  = pa.plc_address

        INNER JOIN F2_Utility_Scada_Channel ch
            ON hi.box_device_id = ch.box_device_id

        INNER JOIN F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id

        WHERE pa.cate_id = 'E_Vol'
          AND hi.value <> 0
          AND hi.recorded_at > DATEADD(DAY, -1, GETDATE())
          AND (:fac = 'KVH' OR sc.fac = :fac)
    ),

    agg AS (
        SELECT
            recorded_minute,
            MIN(value) as minVol,
            MAX(value) as maxVol
        FROM voltage_data
        GROUP BY recorded_minute
    )

    SELECT
        v.recorded_minute,
        v.name,
        MAX(v.value) as value,

        CASE
            WHEN a.minVol < 205 OR a.maxVol > 245
            THEN 'Alarm'
            ELSE 'Normal'
        END as alarm

    FROM voltage_data v
    JOIN agg a
        ON v.recorded_minute = a.recorded_minute

    GROUP BY
        v.recorded_minute,
        v.name,
        a.minVol,
        a.maxVol

    ORDER BY v.recorded_minute
    """, nativeQuery = true)
	List<Map<String,Object>> getVoltageDetail1(@Param("fac") String fac);
}
