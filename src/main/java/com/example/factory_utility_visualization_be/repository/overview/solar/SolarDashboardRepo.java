package com.example.factory_utility_visualization_be.repository.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarCostProjection;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarDailyTrendProjection;
import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarHourlyProfileProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SolarDashboardRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			WITH DeviceMap AS (
			    SELECT DISTINCT
			        ch.box_device_id,
			        sc.fac,
			
			        CASE
			            WHEN UPPER(
			                LTRIM(
			                    RTRIM(
			                        ISNULL(ch.box_id, '')
			                    )
			                )
			            ) = 'SOLAR'
			            THEN 1
			            ELSE 0
			        END AS is_solar
			
			    FROM dbo.F2_Utility_Scada_Channel ch
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			),
			
			EnergyRaw AS (
			    SELECT
			        dm.is_solar,
			
			        CAST(
			            hm.[value]
			            AS DECIMAL(19, 6)
			        ) AS energy_value
			
			    FROM dbo.F2_Utility_Para_History_Main hm
			
			    INNER JOIN dbo.F2_Utility_Para pa
			        ON pa.box_device_id = hm.box_device_id
			       AND pa.plc_address = hm.plc_address
			
			    INNER JOIN DeviceMap dm
			        ON dm.box_device_id = hm.box_device_id
			
			    WHERE
			        pa.name_en = :energyName
			
			        AND hm.pick_at >= :todayStart
			        AND hm.pick_at < :tomorrowStart
			
			        AND hm.[value] > 0
			
			        AND ISNULL(
			            hm.MTD,
			            ''
			        ) = 'MTD'
			
			        AND (
			            UPPER(:fac) = 'KVH'
			            OR UPPER(dm.fac) = UPPER(:fac)
			        )
			),
			
			TodayEnergy AS (
			    SELECT
			        SUM(
			            CASE
			                WHEN is_solar = 0
			                    THEN energy_value
			                ELSE 0
			            END
			        ) AS gross_value,
			
			        SUM(
			            CASE
			                WHEN is_solar = 1
			                    THEN energy_value
			                ELSE 0
			            END
			        ) AS solar_value
			
			    FROM EnergyRaw
			),
			
			SolarPowerDevices AS (
			    SELECT DISTINCT
			        ch.box_device_id
			
			    FROM dbo.F2_Utility_Scada_Channel ch
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        UPPER(
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
			
			SolarPowerParams AS (
			    SELECT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN SolarPowerDevices sd
			        ON sd.box_device_id = pa.box_device_id
			
			    WHERE
			        pa.name_en = :powerName
			),
			
			LatestPowerRanked AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			
			        CAST(
			            hi.[value]
			            AS DECIMAL(19, 6)
			        ) AS power_value,
			
			        ROW_NUMBER() OVER (
			            PARTITION BY
			                hi.box_device_id,
			                hi.plc_address
			
			            ORDER BY
			                hi.recorded_at DESC
			        ) AS row_num
			
			    FROM dbo.F2_Utility_Para_History hi
			
			    INNER JOIN SolarPowerParams sp
			        ON sp.box_device_id = hi.box_device_id
			       AND sp.plc_address = hi.plc_address
			
			    WHERE
			        hi.recorded_at < :now
			        AND hi.[value] >= 0
			),
			
			CurrentPower AS (
			    SELECT
			        SUM(
			            power_value
			        ) AS current_power
			
			    FROM LatestPowerRanked
			
			    WHERE row_num = 1
			)
			
			SELECT
			    CAST(
			        COALESCE(
			            (
			                SELECT current_power
			                FROM CurrentPower
			            ),
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS currentPowerKw,
			
			    CAST(
			        COALESCE(
			            te.solar_value,
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS solarKwh,
			
			    CAST(
			        CASE
			            WHEN COALESCE(
			                te.gross_value,
			                0
			            )
			            >=
			            COALESCE(
			                te.solar_value,
			                0
			            )
			
			            THEN
			                COALESCE(
			                    te.gross_value,
			                    0
			                )
			                -
			                COALESCE(
			                    te.solar_value,
			                    0
			                )
			
			            ELSE 0
			        END
			        AS DECIMAL(19, 1)
			    ) AS gridKwh,
			
			    CAST(
			        COALESCE(
			            te.gross_value,
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS totalKwh,
			
			    CAST(
			        CASE
			            WHEN COALESCE(
			                te.gross_value,
			                0
			            ) > 0
			
			            THEN
			                COALESCE(
			                    te.solar_value,
			                    0
			                )
			                /
			                NULLIF(
			                    te.gross_value,
			                    0
			                )
			                * 100
			
			            ELSE 0
			        END
			        AS DECIMAL(10, 1)
			    ) AS solarSharePercent
			
			FROM TodayEnergy te
			""", nativeQuery = true)
	SolarDashboardProjection getSolarDashboardByToday(
			@Param("fac")
			String fac,

			@Param("todayStart")
			LocalDateTime todayStart,

			@Param("tomorrowStart")
			LocalDateTime tomorrowStart,

			@Param("now")
			LocalDateTime now,

			@Param("powerName")
			String powerName,

			@Param("energyName")
			String energyName
	);


	@Query(value = """
			WITH NormalElectricityParam AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        pa.name_en = :energyName
			
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
			
			SolarElectricityParam AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        pa.name_en = :energyName
			
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
			
			/* =====================================================
			 * ĐIỆN THƯỜNG / GROSS
			 * ===================================================== */
			GrossEnergy AS (
			    SELECT
			        COALESCE(
			            SUM(
			                CAST(
			                    hm.[value]
			                    AS DECIMAL(19, 6)
			                )
			            ),
			            0
			        ) AS gross_value
			
			    FROM NormalElectricityParam ep
			
			    INNER JOIN dbo.F2_Utility_Para_History_Main hm
			        ON hm.box_device_id = ep.box_device_id
			       AND hm.plc_address = ep.plc_address
			
			    WHERE
			        hm.pick_at >= :monthStart
			        AND hm.pick_at < :nextMonthStart
			
			        AND hm.[value] > 0
			
			        -- QUAN TRỌNG:
			        -- giữ dòng này nếu Monthly chính của bạn cũng dùng MTD
			        AND ISNULL(hm.MTD, '') = 'MTD'
			),
			
			/* =====================================================
			 * SOLAR
			 * ===================================================== */
			SolarEnergy AS (
			    SELECT
			        COALESCE(
			            SUM(
			                CAST(
			                    hm.[value]
			                    AS DECIMAL(19, 6)
			                )
			            ),
			            0
			        ) AS solar_value
			
			    FROM SolarElectricityParam ep
			
			    INNER JOIN dbo.F2_Utility_Para_History_Main hm
			        ON hm.box_device_id = ep.box_device_id
			       AND hm.plc_address = ep.plc_address
			
			    WHERE
			        hm.pick_at >= :monthStart
			        AND hm.pick_at < :nextMonthStart
			
			        AND hm.[value] > 0
			
			        AND ISNULL(hm.MTD, '') = 'MTD'
			),
			
			/* =====================================================
			 * SOLAR POWER DEVICES
			 * ===================================================== */
			SolarPowerDevices AS (
			    SELECT DISTINCT
			        ch.box_device_id
			
			    FROM dbo.F2_Utility_Scada_Channel ch
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        UPPER(
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
			
			SolarPowerParams AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN SolarPowerDevices sd
			        ON sd.box_device_id = pa.box_device_id
			
			    WHERE
			        pa.name_en = :powerName
			),
			
			LatestPowerRanked AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			
			        CAST(
			            hi.[value]
			            AS DECIMAL(19, 6)
			        ) AS power_value,
			
			        ROW_NUMBER() OVER (
			            PARTITION BY
			                hi.box_device_id,
			                hi.plc_address
			            ORDER BY
			                hi.recorded_at DESC
			        ) AS row_num
			
			    FROM dbo.F2_Utility_Para_History hi
			
			    INNER JOIN SolarPowerParams sp
			        ON sp.box_device_id = hi.box_device_id
			       AND sp.plc_address = hi.plc_address
			
			    WHERE
			        hi.recorded_at <= :now
			        AND hi.[value] >= 0
			),
			
			CurrentPower AS (
			    SELECT
			        COALESCE(
			            SUM(power_value),
			            0
			        ) AS current_power
			
			    FROM LatestPowerRanked
			
			    WHERE row_num = 1
			)
			
			SELECT
			    CAST(
			        cp.current_power
			        AS DECIMAL(19, 1)
			    ) AS currentPowerKw,
			
			    CAST(
			        se.solar_value
			        AS DECIMAL(19, 1)
			    ) AS solarKwh,
			
			    /* GRID = GROSS - SOLAR */
			    CAST(
			        CASE
			            WHEN ge.gross_value >= se.solar_value
			            THEN ge.gross_value - se.solar_value
			            ELSE 0
			        END
			        AS DECIMAL(19, 1)
			    ) AS gridKwh,
			
			    /* GROSS */
			    CAST(
			        ge.gross_value
			        AS DECIMAL(19, 1)
			    ) AS totalKwh,
			
			    CAST(
			        CASE
			            WHEN ge.gross_value > 0
			            THEN
			                se.solar_value
			                /
			                NULLIF(ge.gross_value, 0)
			                * 100
			            ELSE 0
			        END
			        AS DECIMAL(10, 1)
			    ) AS solarSharePercent
			
			FROM GrossEnergy ge
			CROSS JOIN SolarEnergy se
			CROSS JOIN CurrentPower cp
			""", nativeQuery = true)
	SolarDashboardProjection getSolarDashboardByMonth(

			@Param("fac")
			String fac,

			@Param("monthStart")
			LocalDateTime monthStart,

			@Param("nextMonthStart")
			LocalDateTime nextMonthStart,

			@Param("now")
			LocalDateTime now,

			@Param("powerName")
			String powerName,

			@Param("energyName")
			String energyName
	);


	@Query(value = """
        WITH GrossDaily AS (

            /* =====================================================
             * GROSS ELECTRICITY
             * KHÔNG LẤY SOLAR
             * ===================================================== */
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS record_date,

                SUM(
                    CAST(
                        hi.[value]
                        AS DECIMAL(19, 6)
                    )
                ) AS gross_value

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
               AND pa.name_en = :energyName

            WHERE
                hi.pick_at >= :monthStart
                AND hi.pick_at < :nextMonthStart

                AND hi.[value] > 0

                /* =================================================
                 * KHÔNG SOLAR
                 *
                 * EXISTS tránh duplicate history
                 * ================================================= */
                AND EXISTS (
                    SELECT 1

                    FROM dbo.F2_Utility_Scada_Channel ch

                    INNER JOIN dbo.F2_Utility_Scada sc
                        ON sc.scada_id = ch.scada_id

                    WHERE
                        ch.box_device_id = hi.box_device_id

                        AND UPPER(
                            LTRIM(
                                RTRIM(
                                    ISNULL(
                                        ch.box_id,
                                        ''
                                    )
                                )
                            )
                        ) <> 'SOLAR'

                        AND (
                            UPPER(:fac) = 'KVH'
                            OR UPPER(sc.fac) = UPPER(:fac)
                        )
                )

            GROUP BY
                CAST(
                    hi.pick_at AS DATE
                )
        ),


        SolarDaily AS (

            /* =====================================================
             * SOLAR
             * ===================================================== */
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS record_date,

                SUM(
                    CAST(
                        hi.[value]
                        AS DECIMAL(19, 6)
                    )
                ) AS solar_value

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
               AND pa.name_en = :energyName

            WHERE
                hi.pick_at >= :monthStart
                AND hi.pick_at < :nextMonthStart

                AND hi.[value] > 0

                /* =================================================
                 * CHỈ SOLAR
                 * ================================================= */
                AND EXISTS (
                    SELECT 1

                    FROM dbo.F2_Utility_Scada_Channel ch

                    INNER JOIN dbo.F2_Utility_Scada sc
                        ON sc.scada_id = ch.scada_id

                    WHERE
                        ch.box_device_id = hi.box_device_id

                        AND UPPER(
                            LTRIM(
                                RTRIM(
                                    ISNULL(
                                        ch.box_id,
                                        ''
                                    )
                                )
                            )
                        ) = 'SOLAR'

                        AND (
                            UPPER(:fac) = 'KVH'
                            OR UPPER(sc.fac) = UPPER(:fac)
                        )
                )

            GROUP BY
                CAST(
                    hi.pick_at AS DATE
                )
        ),


        Dates AS (
            SELECT
                record_date
            FROM GrossDaily

            UNION

            SELECT
                record_date
            FROM SolarDaily
        )


        SELECT
            d.record_date AS recordDate,


            /* =====================================================
             * SOLAR
             * ===================================================== */
            CAST(
                COALESCE(
                    s.solar_value,
                    0
                )
                AS DECIMAL(19, 2)
            ) AS solarKwh,


            /* =====================================================
             * GRID
             *
             * GRID = GROSS - SOLAR
             * ===================================================== */
            CAST(
                CASE
                    WHEN
                        COALESCE(
                            g.gross_value,
                            0
                        )
                        >=
                        COALESCE(
                            s.solar_value,
                            0
                        )

                    THEN
                        COALESCE(
                            g.gross_value,
                            0
                        )
                        -
                        COALESCE(
                            s.solar_value,
                            0
                        )

                    ELSE 0
                END
                AS DECIMAL(19, 2)
            ) AS gridKwh,


            /* =====================================================
             * TOTAL / GROSS
             * ===================================================== */
            CAST(
                COALESCE(
                    g.gross_value,
                    0
                )
                AS DECIMAL(19, 2)
            ) AS totalKwh,


            /* =====================================================
             * SOLAR SHARE
             * ===================================================== */
            CAST(
                CASE
                    WHEN
                        COALESCE(
                            g.gross_value,
                            0
                        ) > 0

                    THEN
                        COALESCE(
                            s.solar_value,
                            0
                        )
                        /
                        NULLIF(
                            g.gross_value,
                            0
                        )
                        * 100

                    ELSE 0
                END
                AS DECIMAL(10, 2)
            ) AS solarSharePercent

        FROM Dates d

        LEFT JOIN GrossDaily g
            ON g.record_date = d.record_date

        LEFT JOIN SolarDaily s
            ON s.record_date = d.record_date

        ORDER BY
            d.record_date
        """, nativeQuery = true)
	List<SolarDailyTrendProjection> getSolarDailyTrend(

			@Param("fac")
			String fac,

			@Param("monthStart")
			LocalDateTime monthStart,

			@Param("nextMonthStart")
			LocalDateTime nextMonthStart,

			@Param("energyName")
			String energyName
	);


	@Query(value = """
			WITH Hours AS (
			    SELECT hour_number
			    FROM (
			        VALUES
			            (0),(1),(2),(3),(4),(5),
			            (6),(7),(8),(9),(10),(11),
			            (12),(13),(14),(15),(16),(17),
			            (18),(19),(20),(21),(22),(23)
			    ) h(hour_number)
			),
			
			SolarParam AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        pa.name_en = :energyName
			
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
			
			SolarRaw AS (
			    SELECT
			        CAST(hm.pick_at AS DATE) AS record_date,
			
			        DATEPART(
			            HOUR,
			            hm.pick_at
			        ) AS hour_number,
			
			        CAST(
			            hm.[value]
			            AS DECIMAL(19,6)
			        ) AS solar_value
			
			    FROM SolarParam sp
			
			    INNER JOIN dbo.F2_Utility_Para_History_Main hm
			        ON hm.box_device_id = sp.box_device_id
			       AND hm.plc_address = sp.plc_address
			
			    WHERE
			        hm.pick_at >= :monthStart
			        AND hm.pick_at < :nextMonthStart
			
			        AND hm.[value] > 0
			
			        AND ISNULL(
			            hm.MTD,
			            ''
			        ) = 'MTD'
			),
			
			SolarHourly AS (
			    SELECT
			        record_date,
			        hour_number,
			
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
			
			        SUM(
			            solar_value
			        ) AS solar_kwh
			
			    FROM SolarRaw
			
			    GROUP BY
			        record_date,
			        hour_number
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
			                                24.0
			                                -
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
			                                24.0
			                                -
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
			        NULLIF(
			            total_hours,
			            0
			        ) AS vnd_rate
			
			    FROM HourRate
			
			    WHERE
			        total_hours > 0
			),
			
			CostData AS (
			    SELECT
			        sh.solar_kwh,
			
			        sh.solar_kwh
			        *
			        COALESCE(
			            fr.vnd_rate,
			            0
			        ) AS normal_cost_vnd
			
			    FROM SolarHourly sh
			
			    LEFT JOIN FinalRate fr
			        ON fr.wd = sh.wd
			       AND fr.hour_number = sh.hour_number
			)
			
			SELECT
			    CAST(
			        COALESCE(
			            SUM(solar_kwh),
			            0
			        )
			        AS DECIMAL(19,2)
			    ) AS solarEnergyKwh,
			
			    CAST(
			        COALESCE(
			            SUM(normal_cost_vnd),
			            0
			        )
			        AS DECIMAL(19,2)
			    ) AS normalCostVnd,
			
			    CAST(
			        COALESCE(
			            SUM(normal_cost_vnd),
			            0
			        )
			        * 0.83
			        AS DECIMAL(19,2)
			    ) AS solarCostVnd,
			
			    CAST(
			        COALESCE(
			            SUM(normal_cost_vnd),
			            0
			        )
			        * 0.17
			        AS DECIMAL(19,2)
			    ) AS savingVnd
			
			FROM CostData
			""", nativeQuery = true)
	SolarCostProjection getSolarMonthlyCost(

			@Param("fac")
			String fac,

			@Param("monthStart")
			LocalDateTime monthStart,

			@Param("nextMonthStart")
			LocalDateTime nextMonthStart,

			@Param("energyName")
			String energyName
	);

	@Query(value = """
			WITH SolarParam AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			
			    FROM dbo.F2_Utility_Para pa
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			
			    WHERE
			        pa.name_en = :energyName
			
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
			)
			
			SELECT
			    DATEPART(
			        HOUR,
			        hm.pick_at
			    ) AS scaleHour,
			
			    CAST(
			        SUM(
			            CAST(
			                hm.[value]
			                AS DECIMAL(19,6)
			            )
			        )
			        AS DECIMAL(19,2)
			    ) AS energyKwh
			
			FROM SolarParam sp
			
			INNER JOIN dbo.F2_Utility_Para_History_Main hm
			    ON hm.box_device_id = sp.box_device_id
			   AND hm.plc_address = sp.plc_address
			
			WHERE
			    hm.pick_at >= :dayStart
			    AND hm.pick_at < :nextDayStart
			
			    AND hm.[value] > 0
			
			    AND ISNULL(
			        hm.MTD,
			        ''
			    ) = 'MTD'
			
			GROUP BY
			    DATEPART(
			        HOUR,
			        hm.pick_at
			    )
			
			ORDER BY
			    scaleHour
			""", nativeQuery = true)
	List<SolarHourlyProfileProjection> getSolarHourlyProfile(

			@Param("fac")
			String fac,

			@Param("dayStart")
			LocalDateTime dayStart,

			@Param("nextDayStart")
			LocalDateTime nextDayStart,

			@Param("energyName")
			String energyName
	);
}