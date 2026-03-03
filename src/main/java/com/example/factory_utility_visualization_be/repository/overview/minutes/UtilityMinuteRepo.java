package com.example.factory_utility_visualization_be.repository.overview.minutes;


import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

public interface UtilityMinuteRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        WITH CleanData AS (
            SELECT *
            FROM F2_Utility_Para_History
            WHERE recorded_at > DATEADD(MINUTE, -:minutes, GETDATE())
              AND [value] > 0
        ),
        Base AS (
            SELECT
                sc.fac,
                ch.cate,
                pa.name_en,
                pa.box_device_id,
                hi.[value],
                hi.recorded_at,
                DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0) AS minute_time,
                hi.[value] - LAG(hi.[value]) OVER (
                    PARTITION BY hi.box_device_id, hi.plc_address
                    ORDER BY hi.recorded_at
                ) AS value_per_minute
            FROM CleanData hi
            INNER JOIN F2_Utility_Para pa
                ON hi.box_device_id = pa.box_device_id
               AND hi.plc_address  = pa.plc_address
            INNER JOIN F2_Utility_Scada_Channel ch
                ON hi.box_device_id = ch.box_device_id
            INNER JOIN F2_Utility_Scada sc
                ON ch.scada_id = sc.scada_id
            WHERE pa.name_en = :nameEn
              AND (:fac = 'KVH' OR sc.fac = :fac)
        )
        SELECT
            minute_time AS ts,
            SUM(value_per_minute) AS value
        FROM Base
        WHERE value_per_minute IS NOT NULL
        GROUP BY minute_time
        ORDER BY minute_time
        """, nativeQuery = true)
	List<Object[]> findEnergyDeltaPerMinute(
			@Param("fac") String fac,
			@Param("minutes") int minutes,
			@Param("nameEn") String nameEn
	);

	default List<MinutePointDto> findEnergyDeltaPerMinuteDto(String fac, int minutes, String nameEn) {
		return findEnergyDeltaPerMinute(fac, minutes, nameEn).stream()
				.map(r -> new MinutePointDto(
						((Timestamp) r[0]).toLocalDateTime(),
						r[1] == null ? null : ((Number) r[1]).doubleValue()
				))
				.toList();
	}
}