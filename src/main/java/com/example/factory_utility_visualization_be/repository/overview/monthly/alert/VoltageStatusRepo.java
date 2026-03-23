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
			                MIN(h.value) as minVol,
			                MAX(h.value) as maxVol,
			                CASE
			                    WHEN MIN(h.value) < 205 OR MAX(h.value) > 245
			                    THEN 'Alarm'
			                    ELSE 'Normal'
			                END as alarm
			            FROM F2_Utility_Para_History h
			            JOIN F2_Utility_Scada_Channel c\s
			                ON h.box_device_id = c.box_device_id
			            JOIN F2_Utility_Scada s\s
			                ON c.scada_id = s.scada_id
			            WHERE h.plc_address IN ('D108','D110','D112')
			              AND h.value <> 0
			              AND h.recorded_at > DATEADD(DAY,-1,GETDATE())
			              AND s.fac = :fac   -- 👈 FILTER Ở ĐÂY
			""", nativeQuery = true)
	List<Object[]> getVoltageStatus(@Param("fac") String fac);
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
			        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0) as recorded_minute,
			
			        MAX(CASE WHEN h.plc_address='D108' THEN h.[value] END) AS D108,
			        MAX(CASE WHEN h.plc_address='D110' THEN h.[value] END) AS D110,
			        MAX(CASE WHEN h.plc_address='D112' THEN h.[value] END) AS D112,
			
			        CASE
			            WHEN MAX(CASE WHEN h.plc_address='D108' THEN h.[value] END) NOT BETWEEN 205 AND 245
			              OR MAX(CASE WHEN h.plc_address='D110' THEN h.[value] END) NOT BETWEEN 205 AND 245
			              OR MAX(CASE WHEN h.plc_address='D112' THEN h.[value] END) NOT BETWEEN 205 AND 245
			            THEN 'Alarm'
			            ELSE 'Normal'
			        END AS Alarm
			
			    FROM F2_Utility_Para_History h
			
			    JOIN (
			        SELECT DISTINCT box_device_id, scada_id
			        FROM F2_Utility_Scada_Channel
			    ) c ON h.box_device_id = c.box_device_id
			
			    JOIN F2_Utility_Scada s 
			        ON c.scada_id = s.scada_id
			
			    WHERE h.plc_address IN ('D108','D110','D112')
			      AND h.recorded_at > DATEADD(DAY,-1,GETDATE())
			      AND h.[value] <> 0
			      AND s.fac = :fac
			
			    GROUP BY DATEADD(MINUTE, DATEDIFF(MINUTE, 0, h.recorded_at), 0)
			
			    ORDER BY recorded_minute
			
			""", nativeQuery = true)
	List<Map<String, Object>> getVoltageDetail(@Param("fac") String fac);


}
