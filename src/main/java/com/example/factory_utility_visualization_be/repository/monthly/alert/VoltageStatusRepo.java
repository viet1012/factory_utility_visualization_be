package com.example.factory_utility_visualization_be.repository.monthly.alert;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

	@Query(value = """
        SELECT 
            pick_at as pickAt,
            MAX(CASE WHEN plc_address='D12' THEN value END) as d12,
            MAX(CASE WHEN plc_address='D14' THEN value END) as d14,
            MAX(CASE WHEN plc_address='D16' THEN value END) as d16,

            CASE 
                WHEN MAX(CASE WHEN plc_address='D12' THEN value END) NOT BETWEEN 205 AND 245
                  OR MAX(CASE WHEN plc_address='D14' THEN value END) NOT BETWEEN 205 AND 245
                  OR MAX(CASE WHEN plc_address='D16' THEN value END) NOT BETWEEN 205 AND 245
                THEN 'Alarm'
                ELSE 'Normal'
            END as alarm

        FROM F2_Utility_Para_History_Main

        WHERE plc_address IN ('D12','D14','D16')
        AND pick_at >= DATEADD(HOUR,-24,GETDATE())
        AND value <> 0

        GROUP BY pick_at
        ORDER BY pick_at
        """, nativeQuery = true)
	List<Map<String,Object>> getVoltageDetail1();
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
}
