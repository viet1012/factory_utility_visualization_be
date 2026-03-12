package com.example.factory_utility_visualization_be.repository.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityMonthlyRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			SELECT
			    pa.name_en AS name,
			    ch.cate     AS cate,
			    pa.unit     AS unit,
			    :month      AS month,
			    COALESCE(SUM(hi.[value]), 0) AS value,
				MAX(pick_at) AS pick_at
			FROM F2_Utility_Para_History_Main hi
			
			INNER JOIN F2_Utility_Para pa
			    ON hi.box_device_id = pa.box_device_id
			   AND hi.plc_address  = pa.plc_address
			
			INNER JOIN F2_Utility_Scada_Channel ch
			    ON hi.box_device_id = ch.box_device_id
			
			INNER JOIN F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id
			
			WHERE hi.[value] > 0
			  AND pa.name_en IN (:names)
			  AND hi.pick_at >= :from
			  AND hi.pick_at <  :to
			  AND (:fac = 'KVH' OR sc.fac = :fac)
			
			GROUP BY pa.name_en, ch.cate, pa.unit
			""", nativeQuery = true)
	List<MonthlySummaryProjection> sumMonthlyByNamesRaw(
			@Param("fac") String fac,
			@Param("month") String month,
			@Param("names") List<String> names,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to
	);
}
