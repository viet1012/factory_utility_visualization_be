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
        pa.cate_id       AS cateId,
        pa.box_device_id AS boxDeviceId,
        MIN(hi.value)    AS minVol,
        MAX(hi.value)    AS maxVol,
        MIN(pa.min_alert) AS minVolStd,
        MIN(pa.max_alert) AS maxVolStd,
        IIF(
            MIN(hi.value) < MIN(pa.min_alert)
            OR MAX(hi.value) > MIN(pa.max_alert),
            'Alarm',
            'Normal'
        ) AS alarm
    FROM F2_Utility_Para_History hi
    INNER JOIN F2_Utility_Para pa
        ON hi.box_device_id = pa.box_device_id
       AND hi.plc_address = pa.plc_address
    INNER JOIN F2_Utility_Scada_Channel c
        ON hi.box_device_id = c.box_device_id
    INNER JOIN F2_Utility_Scada s
        ON c.scada_id = s.scada_id
    WHERE pa.is_alert = 1
      AND hi.value <> 0
      AND hi.recorded_at > DATEADD(DAY, -1, GETDATE())
      AND s.fac = :facId
    GROUP BY pa.box_device_id, pa.cate_id
    ORDER BY pa.box_device_id, pa.cate_id
    """, nativeQuery = true)
	List<Object[]> getVoltageStatus(@Param("facId") String facId);

//	@Query(value = """
//
//			    SELECT
//			        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) as recorded_minute,
//
//			        MAX(CASE WHEN h.plc_address='D108' THEN h.[value] END) AS D108,
//			        MAX(CASE WHEN h.plc_address='D110' THEN h.[value] END) AS D110,
//			        MAX(CASE WHEN h.plc_address='D112' THEN h.[value] END) AS D112,
//
//			        CASE
//			            WHEN MAX(CASE WHEN h.plc_address='D108' THEN h.[value] END) NOT BETWEEN 205 AND 245
//			              OR MAX(CASE WHEN h.plc_address='D110' THEN h.[value] END) NOT BETWEEN 205 AND 245
//			              OR MAX(CASE WHEN h.plc_address='D112' THEN h.[value] END) NOT BETWEEN 205 AND 245
//			            THEN 'Alarm'
//			            ELSE 'Normal'
//			        END AS Alarm
//
//			    FROM F2_Utility_Para_History h
//
//			    JOIN (
//			        SELECT DISTINCT box_device_id, scada_id
//			        FROM F2_Utility_Scada_Channel
//			    ) c ON h.box_device_id = c.box_device_id
//
//			    JOIN F2_Utility_Scada s
//			        ON c.scada_id = s.scada_id
//
//			    WHERE h.plc_address IN ('D108','D110','D112')
//			      AND h.recorded_at > DATEADD(DAY,-1,GETDATE())
//			      AND h.[value] <> 0
//			      AND s.fac = :fac
//
//			    GROUP BY DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0)
//
//			    ORDER BY recorded_minute
//
//			""", nativeQuery = true)
//	List<Map<String, Object>> getVoltageDetail(@Param("fac") String fac);

	@Query(value = """
    SELECT
        pa.cate_id AS cate_id,
        pa.box_device_id AS box_device_id,
        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0) AS recorded_minute,
        MIN(hi.value) AS min_vol,
        MAX(hi.value) AS max_vol,
        MIN(pa.min_alert) AS min_vol_std,
        MIN(pa.max_alert) AS max_vol_std,
        IIF(
            MIN(hi.value) < MIN(pa.min_alert)
            OR MAX(hi.value) > MIN(pa.max_alert),
            'Alarm',
            'Normal'
        ) AS alarm
    FROM F2_Utility_Para_History hi
    INNER JOIN F2_Utility_Para pa
        ON hi.box_device_id = pa.box_device_id
       AND hi.plc_address = pa.plc_address
    JOIN F2_Utility_Scada_Channel c
        ON hi.box_device_id = c.box_device_id
    JOIN F2_Utility_Scada s
        ON c.scada_id = s.scada_id
    WHERE pa.is_alert = 1
      AND hi.recorded_at > DATEADD(DAY, -1, GETDATE())
      AND hi.value <> 0
      AND s.fac = :facId
    GROUP BY
        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0),
        pa.cate_id,
        pa.box_device_id
    ORDER BY
        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0),
        pa.cate_id,
        pa.box_device_id
    """, nativeQuery = true)
	List<Map<String, Object>> getVoltageDetail(@Param("facId") String facId);
}

