package com.example.factory_utility_visualization_be.repository.overview.daily;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityDailyRepo extends JpaRepository<DummyEntity, Long> {
	//	@Query(value = """
//    SELECT
//      CAST(hi.pick_at AS date) AS record_date,
//      SUM(hi.value) AS value
//    FROM F2_Utility_Para_History_Main hi
//    JOIN F2_Utility_Para pa
//      ON hi.box_device_id = pa.box_device_id
//     AND hi.plc_address  = pa.plc_address
//    JOIN F2_Utility_Scada_Channel ch
//      ON hi.box_device_id = ch.box_device_id
//    JOIN F2_Utility_Scada sc
//      ON ch.scada_id = sc.scada_id
//    WHERE hi.pick_at >= :from
//      AND hi.pick_at <  :to
//      AND hi.value > 0
//      AND pa.name_en = 'Total Energy Consumption'
//      AND (:fac = 'KVH' OR sc.fac = :fac)
//    GROUP BY CAST(hi.pick_at AS date)
//    ORDER BY record_date
//  """, nativeQuery = true)
//	List<Object[]> sumDailyEnergy(
//			@Param("fac") String fac,
//			@Param("from") LocalDateTime from,
//			@Param("to") LocalDateTime to
//	);
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
            WHERE pa.name_en = 'Total Energy Consumption'
              AND (:fac = 'KVH' OR sc.fac = :fac)
        )
        SELECT
            CAST(wd.[Date] AS DATE) AS recordDate,
            COALESCE(SUM(fd.value), 0) AS value
        FROM F2_Working_Date wd
        LEFT JOIN FilteredData fd
               ON wd.[Date] = fd.record_date
        WHERE wd.[Date] >= :from
          AND wd.[Date] <  :to
        GROUP BY CAST(wd.[Date] AS DATE)
        ORDER BY CAST(wd.[Date] AS DATE)
        """, nativeQuery = true)
	List<Object[]> sumDailyEnergyByMonth(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to
	);
	@Query(value = """
        SELECT
            COALESCE(SUM(hi.[value]), 0) AS totalValue
        FROM F2_Utility_Para_History_Main hi

        INNER JOIN F2_Utility_Para pa
            ON hi.box_device_id = pa.box_device_id
           AND hi.plc_address  = pa.plc_address

        INNER JOIN F2_Utility_Scada_Channel ch
            ON hi.box_device_id = ch.box_device_id

        INNER JOIN F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id

        WHERE hi.[value] > 0
          AND pa.name_en = 'Total Energy Consumption'
          AND hi.pick_at >= :from
          AND hi.pick_at <  :to
          AND (:fac = 'KVH' OR sc.fac = :fac)
        """, nativeQuery = true)
	BigDecimal sumMonthlyEnergy(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to
	);
}
