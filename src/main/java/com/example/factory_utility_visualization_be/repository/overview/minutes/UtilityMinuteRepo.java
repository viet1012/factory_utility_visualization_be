package com.example.factory_utility_visualization_be.repository.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UtilityMinuteRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
            WITH RelevantParams AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address,
                    pa.name_en,

                    CASE
                        WHEN pa.name_en = 'Total Energy Consumption'
                            THEN 'ELECTRICITY'

                        WHEN pa.name_en LIKE '%Cooling tank%'
                            THEN 'WATER'

                        WHEN pa.name_en =
                             'Sensor compressed air pressure Data'
                            THEN 'AIR'

                        ELSE NULL
                    END AS utility_type

                FROM dbo.F2_Utility_Para pa

                WHERE
                    pa.name_en = 'Total Energy Consumption'

                    OR pa.name_en LIKE '%Cooling tank%'

                    OR pa.name_en =
                       'Sensor compressed air pressure Data'
            ),

            DeviceFacility AS (
                SELECT DISTINCT
                    ch.box_device_id,
                    sc.fac

                FROM dbo.F2_Utility_Scada_Channel ch

                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            ),

            RawData AS (
                SELECT
                    rp.utility_type,
                    df.fac,
                    rp.name_en,

                    hi.box_device_id,
                    hi.plc_address,
                    hi.recorded_at,

                    CAST(
                        hi.[value] AS DECIMAL(19, 6)
                    ) AS raw_value

                FROM dbo.F2_Utility_Para_History hi

                INNER JOIN RelevantParams rp
                    ON rp.box_device_id = hi.box_device_id
                   AND rp.plc_address = hi.plc_address

                INNER JOIN DeviceFacility df
                    ON df.box_device_id = hi.box_device_id

                WHERE hi.recorded_at >= :lagFromTime
                  AND hi.recorded_at < :toTime
                  AND hi.[value] > 0

                  AND (
                        :fac = 'KVH'

                        OR (
                            rp.utility_type = 'AIR'
                            AND :fac = 'Fac_A'
                            AND df.fac = 'Fac_B'
                        )

                        OR (
                            NOT (
                                rp.utility_type = 'AIR'
                                AND :fac = 'Fac_A'
                            )
                            AND df.fac = :fac
                        )
                  )
            ),

            DeltaData AS (
                SELECT
                    utility_type,
                    fac,
                    name_en,
                    box_device_id,
                    plc_address,
                    recorded_at,
                    raw_value,

                    raw_value - LAG(raw_value) OVER (
                        PARTITION BY
                            box_device_id,
                            plc_address
                        ORDER BY recorded_at
                    ) AS delta_value

                FROM RawData
            ),

            InRange AS (
                SELECT
                    utility_type,
                    fac,
                    name_en,

                    DATEADD(
                        MINUTE,
                        DATEDIFF(MINUTE, 0, recorded_at),
                        0
                    ) AS minute_time,

                    raw_value,
                    delta_value

                FROM DeltaData

                WHERE recorded_at >= :fromTime
                  AND recorded_at < :toTime
            ),

            ElectricityData AS (
                SELECT
                    'ELECTRICITY' AS utility_type,
                    minute_time,
                    SUM(delta_value) AS result_value,
                    'ELECTRICITY' AS result_name

                FROM InRange

                WHERE utility_type = 'ELECTRICITY'
                  AND delta_value IS NOT NULL
                  AND delta_value >= 0

                GROUP BY minute_time
            ),

            WaterRanked AS (
                SELECT
                    minute_time,
                    fac,
                    name_en,
                    raw_value,

                    ROW_NUMBER() OVER (
                        PARTITION BY
                            minute_time,
                            fac
                        ORDER BY
                            raw_value DESC,
                            name_en
                    ) AS row_num

                FROM InRange

                WHERE utility_type = 'WATER'
            ),

            WaterData AS (
                SELECT
                    'WATER' AS utility_type,
                    minute_time,
                    raw_value AS result_value,

                    CONCAT(
                        fac,
                        ' - ',
                        LTRIM(
                            RTRIM(
                                REPLACE(
                                    REPLACE(
                                        name_en,
                                        'Cooling tank ',
                                        'Tank '
                                    ),
                                    ' temperature data',
                                    ''
                                )
                            )
                        )
                    ) AS result_name

                FROM WaterRanked

                WHERE
                    (
                        :fac = 'KVH'
                        AND row_num = 1
                    )
                    OR :fac <> 'KVH'
            ),

            AirData AS (
                SELECT
                    'AIR' AS utility_type,
                    minute_time,

                    CAST(
                        ROUND(
                            AVG(raw_value),
                            1
                        )
                        AS DECIMAL(19, 1)
                    ) AS result_value,

                    'AIR' AS result_name

                FROM InRange

                WHERE utility_type = 'AIR'

                GROUP BY minute_time
            )

            SELECT
                utility_type AS utilityType,
                minute_time AS ts,
                result_value AS value,
                result_name AS name

            FROM ElectricityData

            UNION ALL

            SELECT
                utility_type,
                minute_time,
                result_value,
                result_name

            FROM WaterData

            UNION ALL

            SELECT
                utility_type,
                minute_time,
                result_value,
                result_name

            FROM AirData

            ORDER BY
                ts,
                utilityType,
                name
            """, nativeQuery = true)
	List<UtilityMinuteProjection> findMinuteDashboard(
			@Param("fac") String fac,
			@Param("lagFromTime") LocalDateTime lagFromTime,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);
}