package com.example.factory_utility_visualization_be.repository.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityDailyRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			WITH FilteredData AS (
			    SELECT
			        CASE
			            WHEN pa.name_en = 'Total Energy Consumption'
			                THEN 'ENERGY'
			
			            WHEN pa.name_en LIKE '%Cooling tank%'
			                THEN 'WATER'
			
			            WHEN pa.name_en = 'Sensor compressed air pressure Data'
			                THEN 'AIR'
			        END AS utility_type,
			
			        CAST(hi.pick_at AS DATE) AS record_date,
			
			        hi.[value]
			
			    FROM dbo.F2_Utility_Para_History_Main hi
			
			    INNER JOIN dbo.F2_Utility_Para pa
			        ON hi.box_device_id = pa.box_device_id
			       AND hi.plc_address = pa.plc_address
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON hi.box_device_id = ch.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id
			
			    WHERE hi.pick_at >= :from
			      AND hi.pick_at < :to
			      AND hi.[value] > 0
			
			      AND (
			            pa.name_en = 'Total Energy Consumption'
			            OR pa.name_en LIKE '%Cooling tank%'
			            OR pa.name_en = 'Sensor compressed air pressure Data'
			      )
			
			      AND (
			            :fac = 'KVH'
			
			            OR (
			                sc.fac = :fac
			                AND pa.name_en <> 'Sensor compressed air pressure Data'
			            )
			
			            OR (
			                :fac = 'Fac_A'
			                AND sc.fac = 'Fac_B'
			                AND pa.name_en = 'Sensor compressed air pressure Data'
			            )
			
			            OR (
			                :fac <> 'Fac_A'
			                AND sc.fac = :fac
			                AND pa.name_en = 'Sensor compressed air pressure Data'
			            )
			      )
			)
			
			SELECT
			    utility_type AS utilityType,
			    record_date AS recordDate,
			
			    CASE
			        WHEN utility_type IN ('WATER', 'AIR')
			            THEN AVG(CAST([value] AS DECIMAL(19, 4)))
			
			        WHEN utility_type = 'ENERGY'
			            THEN SUM(CAST([value] AS DECIMAL(19, 4)))
			
			        ELSE 0
			    END AS [value]
			
			FROM FilteredData
			
			WHERE utility_type IS NOT NULL
			
			GROUP BY
			    utility_type,
			    record_date
			
			ORDER BY
			    record_date,
			    utility_type
			""", nativeQuery = true)
	List<UtilityDailyDashboardProjection> getDailyDashboardByMonth(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to
	);
}
