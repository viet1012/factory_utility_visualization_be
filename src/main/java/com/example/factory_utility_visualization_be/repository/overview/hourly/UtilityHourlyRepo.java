package com.example.factory_utility_visualization_be.repository.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyEnergyCompareProjection;
import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlySensorCompareProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityHourlyRepo
		extends JpaRepository<DummyEntity, Long> {

	// ============================================================
	// 1. ELECTRICITY
	// Không lấy Solar
	// ============================================================

	@Query(value = """
            WITH Hours AS (
                SELECT hour_number
                FROM (
                    VALUES
                        (0), (1), (2), (3), (4), (5),
                        (6), (7), (8), (9), (10), (11),
                        (12), (13), (14), (15), (16), (17),
                        (18), (19), (20), (21), (22), (23)
                ) AS H(hour_number)
            ),

            EnergyBase AS (
                SELECT
                    DATEPART(HOUR, hi.pick_at) AS hour_number,

                    CAST(
                        hi.pick_at AS DATE
                    ) AS record_date,

                    CAST(
                        hi.[value]
                        AS DECIMAL(19, 6)
                    ) AS energy_value

                FROM dbo.F2_Utility_Para_History_Main hi

                INNER JOIN dbo.F2_Utility_Para pa
                    ON pa.box_device_id = hi.box_device_id
                   AND pa.plc_address = hi.plc_address

                INNER JOIN dbo.F2_Utility_Scada_Channel ch
                    ON ch.box_device_id = hi.box_device_id

                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id

                WHERE hi.pick_at >= :fromTime
                  AND hi.pick_at < :toTime

                  AND hi.[value] > 0

                  AND pa.name_en = :nameEn

                  -- ==============================================
                  -- KHÔNG LẤY SOLAR
                  -- ==============================================
                  AND UPPER(
                        LTRIM(
                            RTRIM(
                                ISNULL(ch.box_id, '')
                            )
                        )
                      ) <> 'SOLAR'

                  AND (
                        UPPER(:fac) = 'KVH'
                        OR UPPER(sc.fac) = UPPER(:fac)
                  )
            ),

            HourlyEnergy AS (
                SELECT
                    hour_number,
                    record_date,

                    CASE
                        WHEN (
                            DATEDIFF(
                                DAY,
                                CAST('19000101' AS DATE),
                                record_date
                            ) % 7
                        ) = 6
                        THEN '1'

                        ELSE '2-7'
                    END AS wd,

                    SUM(energy_value) AS hour_value

                FROM EnergyBase

                GROUP BY
                    hour_number,
                    record_date
            ),

            HourRate AS (
                SELECT
                    c.WD AS wd,
                    h.hour_number,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime
                            THEN
                                CASE
                                    WHEN h.hour_number < c.toTime
                                     AND h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    ELSE 0
                                END

                            ELSE
                                CASE
                                    WHEN h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN 24.0 < h.hour_number + 1
                                                THEN 24.0
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    WHEN h.hour_number < c.toTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        h.hour_number

                                    ELSE 0
                                END
                        END
                        * c.vnd
                    ) AS weighted_vnd,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime
                            THEN
                                CASE
                                    WHEN h.hour_number < c.toTime
                                     AND h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    ELSE 0
                                END

                            ELSE
                                CASE
                                    WHEN h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN 24.0 < h.hour_number + 1
                                                THEN 24.0
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    WHEN h.hour_number < c.toTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        h.hour_number

                                    ELSE 0
                                END
                        END
                    ) AS total_hours

                FROM dbo.F2_Utility_Cost_Master c

                CROSS JOIN Hours h

                GROUP BY
                    c.WD,
                    h.hour_number
            ),

            FinalRate AS (
                SELECT
                    wd,
                    hour_number,

                    weighted_vnd
                    /
                    NULLIF(total_hours, 0) AS vnd_rate

                FROM HourRate

                WHERE total_hours > 0
            ),

            CostMapped AS (
                SELECT
                    e.hour_number,
                    e.record_date,
                    e.hour_value,
                    r.vnd_rate

                FROM HourlyEnergy e

                LEFT JOIN FinalRate r
                    ON r.wd = e.wd
                   AND r.hour_number = e.hour_number
            )

            SELECT
                hour_number AS scaleHour,

                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:yesterdayDate AS DATE)
                            THEN hour_value
                        END
                    )
                    AS DECIMAL(19, 4)
                ) AS yesterday,

                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:todayDate AS DATE)
                            THEN hour_value
                        END
                    )
                    AS DECIMAL(19, 4)
                ) AS today,

                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:yesterdayDate AS DATE)
                            THEN
                                hour_value
                                * ISNULL(vnd_rate, 0)
                        END
                    )
                    /
                    NULLIF(:exchange, 0)
                    *
                    :sepzone

                    AS DECIMAL(19, 4)
                ) AS yesterdayUsd,

                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:todayDate AS DATE)
                            THEN
                                hour_value
                                * ISNULL(vnd_rate, 0)
                        END
                    )
                    /
                    NULLIF(:exchange, 0)
                    *
                    :sepzone

                    AS DECIMAL(19, 4)
                ) AS todayUsd

            FROM CostMapped

            GROUP BY
                hour_number

            ORDER BY
                hour_number
            """, nativeQuery = true)
	List<HourlyEnergyCompareProjection>
	findHourlyElectricityCompare(

			@Param("fac")
			String fac,

			@Param("fromTime")
			LocalDateTime fromTime,

			@Param("toTime")
			LocalDateTime toTime,

			@Param("todayDate")
			LocalDateTime todayDate,

			@Param("yesterdayDate")
			LocalDateTime yesterdayDate,

			@Param("nameEn")
			String nameEn,

			@Param("exchange")
			BigDecimal exchange,

			@Param("sepzone")
			BigDecimal sepzone
	);


	// ============================================================
	// 2. SOLAR
	//
	// Giá Solar = giá điện hiện tại - 17%
	//           = giá điện hiện tại * 0.83
	// ============================================================

	@Query(value = """
            WITH Hours AS (
                SELECT hour_number
                FROM (
                    VALUES
                        (0), (1), (2), (3), (4), (5),
                        (6), (7), (8), (9), (10), (11),
                        (12), (13), (14), (15), (16), (17),
                        (18), (19), (20), (21), (22), (23)
                ) AS H(hour_number)
            ),

            SolarBase AS (
                SELECT
                    DATEPART(
                        HOUR,
                        hi.pick_at
                    ) AS hour_number,

                    CAST(
                        hi.pick_at AS DATE
                    ) AS record_date,

                    CAST(
                        hi.[value]
                        AS DECIMAL(19, 6)
                    ) AS solar_value

                FROM dbo.F2_Utility_Para_History_Main hi

                INNER JOIN dbo.F2_Utility_Para pa
                    ON pa.box_device_id = hi.box_device_id
                   AND pa.plc_address = hi.plc_address

                INNER JOIN dbo.F2_Utility_Scada_Channel ch
                    ON ch.box_device_id = hi.box_device_id

                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id

                WHERE hi.pick_at >= :fromTime
                  AND hi.pick_at < :toTime

                  AND hi.[value] > 0

                  AND pa.name_en = :nameEn

                  -- ==============================================
                  -- CHỈ LẤY SOLAR
                  -- ==============================================
                  AND UPPER(
                        LTRIM(
                            RTRIM(
                                ISNULL(ch.box_id, '')
                            )
                        )
                      ) = 'SOLAR'

                  AND (
                        UPPER(:fac) = 'KVH'
                        OR UPPER(sc.fac) = UPPER(:fac)
                  )
            ),

            HourlySolar AS (
                SELECT
                    hour_number,
                    record_date,

                    CASE
                        WHEN (
                            DATEDIFF(
                                DAY,
                                CAST('19000101' AS DATE),
                                record_date
                            ) % 7
                        ) = 6
                        THEN '1'

                        ELSE '2-7'
                    END AS wd,

                    SUM(solar_value) AS hour_value

                FROM SolarBase

                GROUP BY
                    hour_number,
                    record_date
            ),

            HourRate AS (
                SELECT
                    c.WD AS wd,
                    h.hour_number,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime
                            THEN
                                CASE
                                    WHEN h.hour_number < c.toTime
                                     AND h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    ELSE 0
                                END

                            ELSE
                                CASE
                                    WHEN h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN 24.0 < h.hour_number + 1
                                                THEN 24.0
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    WHEN h.hour_number < c.toTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        h.hour_number

                                    ELSE 0
                                END
                        END
                        * c.vnd
                    ) AS weighted_vnd,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime
                            THEN
                                CASE
                                    WHEN h.hour_number < c.toTime
                                     AND h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    ELSE 0
                                END

                            ELSE
                                CASE
                                    WHEN h.hour_number + 1 > c.frTime
                                    THEN
                                        (
                                            CASE
                                                WHEN 24.0 < h.hour_number + 1
                                                THEN 24.0
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        (
                                            CASE
                                                WHEN c.frTime > h.hour_number
                                                THEN c.frTime
                                                ELSE h.hour_number
                                            END
                                        )

                                    WHEN h.hour_number < c.toTime
                                    THEN
                                        (
                                            CASE
                                                WHEN c.toTime < h.hour_number + 1
                                                THEN c.toTime
                                                ELSE h.hour_number + 1
                                            END
                                        )
                                        -
                                        h.hour_number

                                    ELSE 0
                                END
                        END
                    ) AS total_hours

                FROM dbo.F2_Utility_Cost_Master c

                CROSS JOIN Hours h

                GROUP BY
                    c.WD,
                    h.hour_number
            ),

            FinalRate AS (
                SELECT
                    wd,
                    hour_number,

                    weighted_vnd
                    /
                    NULLIF(total_hours, 0) AS vnd_rate

                FROM HourRate

                WHERE total_hours > 0
            ),

            CostMapped AS (
                SELECT
                    s.hour_number,
                    s.record_date,
                    s.hour_value,
                    r.vnd_rate

                FROM HourlySolar s

                LEFT JOIN FinalRate r
                    ON r.wd = s.wd
                   AND r.hour_number = s.hour_number
            )

            SELECT
                hour_number AS scaleHour,

                -- ==============================================
                -- SOLAR kWh HÔM QUA
                -- ==============================================
                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:yesterdayDate AS DATE)
                            THEN hour_value
                        END
                    )
                    AS DECIMAL(19, 4)
                ) AS yesterday,

                -- ==============================================
                -- SOLAR kWh HÔM NAY
                -- ==============================================
                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:todayDate AS DATE)
                            THEN hour_value
                        END
                    )
                    AS DECIMAL(19, 4)
                ) AS today,

                -- ==============================================
                -- SOLAR COST HÔM QUA
                --
                -- Giá Solar = giá điện * 83%
                -- ==============================================
                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:yesterdayDate AS DATE)
                            THEN
                                hour_value
                                * ISNULL(vnd_rate, 0)
                                * CAST(0.83 AS DECIMAL(19, 6))
                        END
                    )
                    /
                    NULLIF(:exchange, 0)
                    *
                    :sepzone

                    AS DECIMAL(19, 4)
                ) AS yesterdayUsd,

                -- ==============================================
                -- SOLAR COST HÔM NAY
                -- ==============================================
                CAST(
                    SUM(
                        CASE
                            WHEN record_date =
                                 CAST(:todayDate AS DATE)
                            THEN
                                hour_value
                                * ISNULL(vnd_rate, 0)
                                * CAST(0.83 AS DECIMAL(19, 6))
                        END
                    )
                    /
                    NULLIF(:exchange, 0)
                    *
                    :sepzone

                    AS DECIMAL(19, 4)
                ) AS todayUsd

            FROM CostMapped

            GROUP BY
                hour_number

            ORDER BY
                hour_number
            """, nativeQuery = true)
	List<HourlyEnergyCompareProjection>
	findHourlySolarCompare(

			@Param("fac")
			String fac,

			@Param("fromTime")
			LocalDateTime fromTime,

			@Param("toTime")
			LocalDateTime toTime,

			@Param("todayDate")
			LocalDateTime todayDate,

			@Param("yesterdayDate")
			LocalDateTime yesterdayDate,

			@Param("nameEn")
			String nameEn,

			@Param("exchange")
			BigDecimal exchange,

			@Param("sepzone")
			BigDecimal sepzone
	);


	// ============================================================
	// SENSOR
	// ============================================================

	@Query(value = """
        WITH ParaDedup AS (
            SELECT DISTINCT
                pa.box_device_id,
                pa.plc_address,
                pa.name_en

            FROM dbo.F2_Utility_Para pa

            WHERE
                   pa.name_en LIKE 'Cooling tank%'
                OR pa.name_en =
                   'Sensor compressed air pressure Data'
        ),

        DeviceMap AS (
            SELECT
                ch.box_device_id,

                MAX(
                    CASE
                        WHEN UPPER(sc.fac) = UPPER(:fac)
                        THEN 1
                        ELSE 0
                    END
                ) AS same_fac,

                MAX(
                    CASE
                        WHEN UPPER(:fac) = 'FAC_A'
                         AND UPPER(sc.fac) = 'FAC_B'
                        THEN 1
                        ELSE 0
                    END
                ) AS air_fac_match

            FROM dbo.F2_Utility_Scada_Channel ch

            INNER JOIN dbo.F2_Utility_Scada sc
                ON sc.scada_id = ch.scada_id

            GROUP BY
                ch.box_device_id
        ),

        RawData AS (
            SELECT
                DATEPART(
                    HOUR,
                    hi.pick_at
                ) AS scale_hour,

                CAST(
                    hi.pick_at AS DATE
                ) AS record_date,

                pa.name_en,

                CAST(
                    hi.[value]
                    AS DECIMAL(19,4)
                ) AS value,

                dm.same_fac,
                dm.air_fac_match

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN ParaDedup pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address

            INNER JOIN DeviceMap dm
                ON dm.box_device_id = hi.box_device_id

            WHERE
                hi.pick_at >= :fromTime
                AND hi.pick_at < :toTime

                AND hi.[value] > 0
        ),

        HourlyWater AS (
            SELECT
                scale_hour,

                record_date,

                CAST(
                    AVG(value)
                    AS DECIMAL(19,4)
                ) AS hour_value

            FROM RawData

            WHERE
                name_en LIKE 'Cooling tank%'

                AND (
                    UPPER(:fac) = 'KVH'
                    OR same_fac = 1
                )

            GROUP BY
                scale_hour,
                record_date
        ),

        HourlyAir AS (
            SELECT
                scale_hour,

                record_date,

                CAST(
                    AVG(value)
                    AS DECIMAL(19,4)
                ) AS hour_value

            FROM RawData

            WHERE
                name_en =
                    'Sensor compressed air pressure Data'

                AND (
                    UPPER(:fac) = 'KVH'

                    OR (
                        UPPER(:fac) = 'FAC_A'
                        AND air_fac_match = 1
                    )

                    OR (
                        UPPER(:fac) <> 'FAC_A'
                        AND same_fac = 1
                    )
                )

            GROUP BY
                scale_hour,
                record_date
        ),

        AllSensor AS (

            SELECT
                scale_hour,
                record_date,
                'WATER' AS utility_type,
                hour_value

            FROM HourlyWater


            UNION ALL


            SELECT
                scale_hour,
                record_date,
                'AIR' AS utility_type,
                hour_value

            FROM HourlyAir
        )

        SELECT
            scale_hour AS scaleHour,

            utility_type AS utilityType,

            CAST(
                MAX(
                    CASE
                        WHEN record_date =
                             CAST(:yesterdayDate AS DATE)
                        THEN hour_value
                    END
                )
                AS DECIMAL(19,4)
            ) AS yesterday,

            CAST(
                MAX(
                    CASE
                        WHEN record_date =
                             CAST(:todayDate AS DATE)
                        THEN hour_value
                    END
                )
                AS DECIMAL(19,4)
            ) AS today

        FROM AllSensor

        GROUP BY
            scale_hour,
            utility_type

        ORDER BY
            scale_hour,
            utility_type
        """, nativeQuery = true)
	List<HourlySensorCompareProjection>
	findHourlySensorCompare(

			@Param("fac")
			String fac,

			@Param("fromTime")
			LocalDateTime fromTime,

			@Param("toTime")
			LocalDateTime toTime,

			@Param("todayDate")
			LocalDateTime todayDate,

			@Param("yesterdayDate")
			LocalDateTime yesterdayDate
	);
}