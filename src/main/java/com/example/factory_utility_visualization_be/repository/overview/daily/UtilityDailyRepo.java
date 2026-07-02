package com.example.factory_utility_visualization_be.repository.overview.daily;

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
			WITH CleanData AS (
			    SELECT *
			    FROM F2_Utility_Para_History_Main
			    WHERE [value] > 0
			),
			FilteredData AS (
			    SELECT
			        sc.fac,
			        CAST(hi.pick_at AS DATE) AS record_date,
			        hi.[value]
			    FROM CleanData hi
			    INNER JOIN F2_Utility_Para pa
			        ON hi.box_device_id = pa.box_device_id
			       AND hi.plc_address  = pa.plc_address
			    INNER JOIN F2_Utility_Scada_Channel ch
			        ON hi.box_device_id = ch.box_device_id
			    INNER JOIN F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id
			    WHERE
			    (
			        (:type = 'ENERGY' AND pa.name_en = :nameEn)
			
			        OR
			
			        (:type = 'WATER'
			            AND pa.name_en LIKE '%Cooling tank%')
			
			        OR
			
			        (:type = 'AIR'
			            AND pa.name_en = 'Sensor compressed air pressure Data')
			    )
			    AND (
			           :fac = 'KVH'
			        OR (:type = 'AIR' AND :fac = 'Fac_A' AND sc.fac = 'Fac_B')
			        OR sc.fac = :fac
			    )
			    AND hi.pick_at >= :from
			    AND hi.pick_at < :to
			)
			SELECT
			    record_date AS recordDate,
			    CASE
			        WHEN :type IN ('WATER','AIR')
			            THEN AVG(value)
			        ELSE SUM(value)
			    END AS value
			FROM FilteredData
			GROUP BY record_date
			ORDER BY record_date
			""", nativeQuery = true)
	List<Object[]> getDailyUtilityByMonth(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("nameEn") String nameEn,
			@Param("type") String type
	);
}
