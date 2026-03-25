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
        s.fac as fac,
        CAST(pa.box_device_id AS varchar) as boxDeviceId,
        'Voltage' as name,
        MIN(h.value) as minVol,
        MAX(h.value) as maxVol,
        CASE
         --   WHEN MIN(h.value) < 323 OR MAX(h.value) > 437 THEN 'Critical'
            WHEN MIN(h.value) < 342 OR MAX(h.value) > 390 THEN 'Alarm'
            WHEN (MAX(h.value) - MIN(h.value)) > (0.1 * AVG(h.value)) THEN 'Alarm'
       --     WHEN MIN(h.value) < 360 OR MAX(h.value) > 400 THEN 'Warning'
        --    WHEN (MAX(h.value) - MIN(h.value)) > (0.05 * AVG(h.value)) THEN 'Warning'
            ELSE 'Normal'
        END as alarm
    FROM F2_Utility_Para_History h
    INNER JOIN F2_Utility_Para pa
        ON h.box_device_id = pa.box_device_id
       AND h.plc_address = pa.plc_address
    JOIN F2_Utility_Scada_Channel c
        ON h.box_device_id = c.box_device_id
    JOIN F2_Utility_Scada s
        ON c.scada_id = s.scada_id
    WHERE h.plc_address IN ('D108', 'D110', 'D112')
      AND h.value <> 0
      AND h.recorded_at > DATEADD(DAY, -1, GETDATE())
      AND s.fac = :fac
    GROUP BY s.fac, pa.box_device_id
    """, nativeQuery = true)
	List<Object[]> getVoltageStatus(@Param("fac") String fac);

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
			    DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) as recorded_minute,
			
			    MAX(CASE WHEN h.plc_address = 'D108' THEN h.value END) as D108,
			    MAX(CASE WHEN h.plc_address = 'D110' THEN h.value END) as D110,
			    MAX(CASE WHEN h.plc_address = 'D112' THEN h.value END) as D112,
			
			    CASE
			     --   WHEN MIN(h.value) < 323 OR MAX(h.value) > 437 THEN 'Critical'
			        WHEN MIN(h.value) < 342 OR MAX(h.value) > 418 THEN 'Alarm'
			     --   WHEN MIN(h.value) < 360 OR MAX(h.value) > 400 THEN 'Warning'
			        ELSE 'Normal'
			    END as alarm
			
			FROM F2_Utility_Para_History h
			JOIN F2_Utility_Scada_Channel c
			    ON h.box_device_id = c.box_device_id
			JOIN F2_Utility_Scada s
			    ON c.scada_id = s.scada_id
			
			WHERE h.plc_address IN ('D108','D110','D112')
			  AND h.value <> 0
			  AND h.recorded_at > DATEADD(DAY,-1,GETDATE())
			  AND s.fac = :fac
			
			GROUP BY DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0)
			
			ORDER BY recorded_minute DESC
			""", nativeQuery = true)
	List<Map<String, Object>> getVoltageDetail(@Param("fac") String fac);
}

