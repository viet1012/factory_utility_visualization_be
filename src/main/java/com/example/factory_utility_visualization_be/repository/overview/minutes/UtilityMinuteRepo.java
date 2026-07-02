//package com.example.factory_utility_visualization_be.repository.overview.minutes;
//
//
//import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
//import com.example.factory_utility_visualization_be.model.DummyEntity;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//
//import java.sql.Timestamp;
//import java.util.List;
//
//public interface UtilityMinuteRepo extends JpaRepository<DummyEntity, Long> {
//
//	@Query(value = """
//			WITH CleanData AS (
//			    SELECT *
//			    FROM F2_Utility_Para_History
//			    WHERE recorded_at > DATEADD(MINUTE, -:minutes, GETDATE())
//			      AND [value] > 0
//			),
//			Base AS (
//			    SELECT
//			        sc.fac,
//			        ch.cate,
//			        pa.name_en,
//			        pa.box_device_id,
//			        hi.[value],
//			        hi.recorded_at,
//			        DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0) AS minute_time,
//			        hi.[value] - LAG(hi.[value]) OVER (
//			            PARTITION BY hi.box_device_id, hi.plc_address
//			            ORDER BY hi.recorded_at
//			        ) AS value_per_minute
//			    FROM CleanData hi
//			    INNER JOIN F2_Utility_Para pa
//			        ON hi.box_device_id = pa.box_device_id
//			       AND hi.plc_address  = pa.plc_address
//			    INNER JOIN F2_Utility_Scada_Channel ch
//			        ON hi.box_device_id = ch.box_device_id
//			    INNER JOIN F2_Utility_Scada sc
//			        ON ch.scada_id = sc.scada_id
//			    WHERE pa.name_en = :nameEn
//			      AND (:fac = 'KVH' OR sc.fac = :fac)
//			)
//			SELECT
//			    minute_time AS ts,
//			    SUM(value_per_minute) AS value
//			FROM Base
//			WHERE value_per_minute IS NOT NULL
//			GROUP BY minute_time
//			ORDER BY minute_time
//			""", nativeQuery = true)
//	List<Object[]> findEnergyDeltaPerMinute(
//			@Param("fac") String fac,
//			@Param("minutes") int minutes,
//			@Param("nameEn") String nameEn
//	);
//
//	default List<MinutePointDto> findEnergyDeltaPerMinuteDto(String fac, int minutes, String nameEn) {
//		return findEnergyDeltaPerMinute(fac, minutes, nameEn).stream()
//				.map(r -> new MinutePointDto(
//						((Timestamp) r[0]).toLocalDateTime(),
//						r[1] == null ? null : ((Number) r[1]).doubleValue()
//				))
//				.toList();
//	}
//
//}
package com.example.factory_utility_visualization_be.repository.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.MinutePointDto;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

//public interface UtilityMinuteRepo extends JpaRepository<DummyEntity, Long> {
//
//	@Query(value = """
//    WITH CleanData AS (
//        SELECT *
//        FROM F2_Utility_Para_History
//        WHERE recorded_at > DATEADD(MINUTE, -:minutes, GETDATE())
//          AND [value] > 0
//    ),
//    Base AS (
//        SELECT
//            sc.fac,
//            ch.cate,
//            pa.name_en,
//            pa.box_device_id,
//            hi.plc_address,
//            hi.[value],
//            hi.recorded_at,
//
//            DATEADD(MINUTE, DATEDIFF(MINUTE, 0, hi.recorded_at), 0) AS minute_time,
//
//            hi.[value] - LAG(hi.[value]) OVER (
//                PARTITION BY hi.box_device_id, hi.plc_address
//                ORDER BY hi.recorded_at
//            ) AS value_per_minute
//
//        FROM CleanData hi
//
//        INNER JOIN F2_Utility_Para pa
//            ON hi.box_device_id = pa.box_device_id
//           AND hi.plc_address  = pa.plc_address
//
//        INNER JOIN F2_Utility_Scada_Channel ch
//            ON hi.box_device_id = ch.box_device_id
//
//        INNER JOIN F2_Utility_Scada sc
//            ON ch.scada_id = sc.scada_id
//
//        WHERE (:fac = 'KVH' OR sc.fac = :fac)
//          AND (
//                (:type = 'ELECTRICITY'
//                    AND pa.name_en = 'Total Energy Consumption')
//
//             OR (:type = 'WATER'
//                    AND pa.name_en LIKE '%Cooling tank%')
//
//             OR (:type = 'AIR'
//                    AND pa.name_en = 'Slave sensor compressed air pressure')
//          )
//    )
//    SELECT
//        minute_time AS ts,
//
//        CASE
//            WHEN :type IN ('WATER', 'AIR')
//                THEN AVG([value])
//            ELSE SUM(value_per_minute)
//        END AS value
//
//    FROM Base
//    WHERE
//        (:type IN ('WATER', 'AIR'))
//        OR (
//            value_per_minute IS NOT NULL
//            AND value_per_minute >= 0
//        )
//
//    GROUP BY minute_time
//    ORDER BY minute_time
//    """, nativeQuery = true)
//	List<Object[]> findUtilityDeltaPerMinute(
//			@Param("fac") String fac,
//			@Param("minutes") int minutes,
//			@Param("type") String type
//	);
//
//	default List<MinutePointDto> findUtilityDeltaPerMinuteDto(
//			String fac,
//			int minutes,
//			String type
//	) {
//		return findUtilityDeltaPerMinute(fac, minutes, type).stream()
//				.map(r -> new MinutePointDto(
//						((Timestamp) r[0]).toLocalDateTime(),
//						r[1] == null ? null : ((Number) r[1]).doubleValue()
//				))
//				.toList();
//	}
//}
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
                hi.plc_address,
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

		    WHERE (
			           :fac = 'KVH'
			        OR (:type = 'AIR' AND :fac = 'Fac_A' AND sc.fac = 'Fac_B')
			        OR sc.fac = :fac
			    )
              AND (
                    (:type = 'ELECTRICITY'
                        AND pa.name_en = 'Total Energy Consumption')

                 OR (:type = 'WATER'
                        AND pa.name_en LIKE '%Cooling tank%')

                 OR (:type = 'AIR'
                        AND pa.name_en = 'Sensor compressed air pressure Data')
              )
        )

        SELECT
            minute_time AS ts,
            SUM(value_per_minute) AS value,
            'ELECTRICITY' AS name
        FROM Base
        WHERE :type = 'ELECTRICITY'
          AND value_per_minute IS NOT NULL
          AND value_per_minute >= 0
        GROUP BY
            minute_time

        UNION ALL

        SELECT
            minute_time AS ts,
            [value] AS value,
            CONCAT(
                fac,
                ' - ',
                LTRIM(RTRIM(
                    REPLACE(
                        REPLACE(name_en, 'Cooling tank ', 'Tank '),
                        ' temperature data', ''
                    )
                ))
            ) AS name
        FROM (
            SELECT
                minute_time,
                fac,
                name_en,
                [value],
                ROW_NUMBER() OVER (
                    PARTITION BY minute_time, fac
                    ORDER BY [value] DESC
                ) AS rn_fac
            FROM Base
            WHERE :type = 'WATER'
        ) x
        WHERE
            (:fac = 'KVH' AND rn_fac = 1)
            OR
            (:fac <> 'KVH')

        UNION ALL

        SELECT
            minute_time AS ts,
		    CAST(
		        ROUND(AVG(CAST([value] AS DECIMAL(10,2))), 1)
		        AS DECIMAL(10,1)
		    ) AS value,
            'AIR' AS name
        FROM Base
        WHERE :type = 'AIR'
        GROUP BY
            minute_time

        ORDER BY
            ts,
            name
        """, nativeQuery = true)
	List<Object[]> findUtilityDeltaPerMinute(
			@Param("fac") String fac,
			@Param("minutes") int minutes,
			@Param("type") String type
	);

	default List<MinutePointDto> findUtilityDeltaPerMinuteDto(
			String fac,
			int minutes,
			String type
	) {
		return findUtilityDeltaPerMinute(fac, minutes, type).stream()
				.map(r -> new MinutePointDto(
						((Timestamp) r[0]).toLocalDateTime(),
						r[1] == null ? null : ((Number) r[1]).doubleValue(),
						r[2] == null ? null : r[2].toString()
				))
				.toList();
	}
}