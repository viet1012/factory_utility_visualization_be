package com.example.factory_utility_visualization_be.repository.overview.solar;

import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarHourEnergyProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarDailyTrendProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarHourlyProfileProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solar endpoints. Same energy rules as every electricity API
 * (README "Tính tiền điện"):
 * - a History_Main row holds the energy of the hour ENDING at pick_at, so its
 *   hour / day is pick_at - 1h (windows are [start + 1h, end + 1h) on pick_at)
 * - MTD = 'MTD', value > 0
 * - a meter with any SOLAR channel is solar, never grid ("solar wins")
 * - :excludedMeters (upper-case box_device_id, never empty) are skipped
 */
public interface SolarDashboardRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
    WITH GridElectricityParam AS (
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

            AND NOT EXISTS (
                SELECT 1
                FROM dbo.F2_Utility_Scada_Channel chs
                WHERE chs.box_device_id = pa.box_device_id
                  AND UPPER(LTRIM(RTRIM(ISNULL(chs.box_id, '')))) = 'SOLAR'
            )

            AND UPPER(LTRIM(RTRIM(pa.box_device_id))) NOT IN (:excludedMeters)
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

            AND UPPER(LTRIM(RTRIM(pa.box_device_id))) NOT IN (:excludedMeters)
    ),

    GridEnergy AS (
        SELECT
            COALESCE(
                SUM(
                    CAST(
                        hm.[value]
                        AS DECIMAL(19,6)
                    )
                ),
                0
            ) AS grid_value

        FROM GridElectricityParam ep

        INNER JOIN dbo.F2_Utility_Para_History_Main hm
            ON hm.box_device_id = ep.box_device_id
           AND hm.plc_address = ep.plc_address

        WHERE
            hm.pick_at >= DATEADD(HOUR, 1, :monthStart)
            AND hm.pick_at < DATEADD(HOUR, 1, :nextMonthStart)

            AND hm.[value] > 0

            AND ISNULL(hm.MTD, '') = 'MTD'
    ),

    SolarEnergy AS (
        SELECT
            COALESCE(
                SUM(
                    CAST(
                        hm.[value]
                        AS DECIMAL(19,6)
                    )
                ),
                0
            ) AS solar_value

        FROM SolarElectricityParam ep

        INNER JOIN dbo.F2_Utility_Para_History_Main hm
            ON hm.box_device_id = ep.box_device_id
           AND hm.plc_address = ep.plc_address

        WHERE
            hm.pick_at >= DATEADD(HOUR, 1, :monthStart)
            AND hm.pick_at < DATEADD(HOUR, 1, :nextMonthStart)

            AND hm.[value] > 0

            AND ISNULL(hm.MTD, '') = 'MTD'
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
                AS DECIMAL(19,6)
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

        WHERE
            row_num = 1
    )

    SELECT
        CAST(
            cp.current_power
            AS DECIMAL(19,1)
        ) AS currentPowerKw,

        -- Solar
        CAST(
            se.solar_value
            AS DECIMAL(19,1)
        ) AS solarKwh,

        -- GRID giữ nguyên
        CAST(
            ge.grid_value
            AS DECIMAL(19,1)
        ) AS gridKwh,

        -- TOTAL = GRID + SOLAR
        CAST(
            ge.grid_value
            +
            se.solar_value
            AS DECIMAL(19,1)
        ) AS totalKwh,

        -- SOLAR SHARE
        CAST(
            CASE
                WHEN
                    ge.grid_value
                    +
                    se.solar_value > 0

                THEN
                    se.solar_value
                    /
                    NULLIF(
                        ge.grid_value
                        +
                        se.solar_value,
                        0
                    )
                    * 100

                ELSE 0
            END
            AS DECIMAL(10,1)
        ) AS solarSharePercent

    FROM GridEnergy ge

    CROSS JOIN SolarEnergy se
    CROSS JOIN CurrentPower cp
    """, nativeQuery = true)
	SolarDashboardProjection getSolarDashboardByMonth(
			@Param("fac") String fac,
			@Param("monthStart") LocalDateTime monthStart,
			@Param("nextMonthStart") LocalDateTime nextMonthStart,
			@Param("now") LocalDateTime now,
			@Param("powerName") String powerName,
			@Param("energyName") String energyName,
			@Param("excludedMeters") List<String> excludedMeters
	);


	@Query(value = """
    WITH GridDaily AS (

        -- =====================================================
        -- GRID / ĐIỆN THƯỜNG
        -- Không lấy Solar
        -- =====================================================

        SELECT
            CAST(
                DATEADD(HOUR, -1, hi.pick_at) AS DATE
            ) AS record_date,

            SUM(
                CAST(
                    hi.[value]
                    AS DECIMAL(19,6)
                )
            ) AS grid_value

        FROM dbo.F2_Utility_Para_History_Main hi

        INNER JOIN dbo.F2_Utility_Para pa
            ON pa.box_device_id = hi.box_device_id
           AND pa.plc_address = hi.plc_address
           AND pa.name_en = :energyName

        WHERE
            hi.pick_at >= DATEADD(HOUR, 1, :monthStart)
            AND hi.pick_at < DATEADD(HOUR, 1, :nextMonthStart)

            AND hi.[value] > 0

            AND ISNULL(hi.MTD, '') = 'MTD'

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
                                ISNULL(ch.box_id, '')
                            )
                        )
                    ) <> 'SOLAR'

                    AND (
                        UPPER(:fac) = 'KVH'
                        OR UPPER(sc.fac) = UPPER(:fac)
                    )
            )

            AND NOT EXISTS (
                SELECT 1
                FROM dbo.F2_Utility_Scada_Channel chs
                WHERE chs.box_device_id = hi.box_device_id
                  AND UPPER(LTRIM(RTRIM(ISNULL(chs.box_id, '')))) = 'SOLAR'
            )

            AND UPPER(LTRIM(RTRIM(hi.box_device_id))) NOT IN (:excludedMeters)

        GROUP BY
            CAST(
                DATEADD(HOUR, -1, hi.pick_at) AS DATE
            )
    ),

    SolarDaily AS (

        -- =====================================================
        -- SOLAR
        -- Chỉ lấy Solar
        -- =====================================================

        SELECT
            CAST(
                DATEADD(HOUR, -1, hi.pick_at) AS DATE
            ) AS record_date,

            SUM(
                CAST(
                    hi.[value]
                    AS DECIMAL(19,6)
                )
            ) AS solar_value

        FROM dbo.F2_Utility_Para_History_Main hi

        INNER JOIN dbo.F2_Utility_Para pa
            ON pa.box_device_id = hi.box_device_id
           AND pa.plc_address = hi.plc_address
           AND pa.name_en = :energyName

        WHERE
            hi.pick_at >= DATEADD(HOUR, 1, :monthStart)
            AND hi.pick_at < DATEADD(HOUR, 1, :nextMonthStart)

            AND hi.[value] > 0

            AND ISNULL(hi.MTD, '') = 'MTD'

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
                                ISNULL(ch.box_id, '')
                            )
                        )
                    ) = 'SOLAR'

                    AND (
                        UPPER(:fac) = 'KVH'
                        OR UPPER(sc.fac) = UPPER(:fac)
                    )
            )

            AND UPPER(LTRIM(RTRIM(hi.box_device_id))) NOT IN (:excludedMeters)

        GROUP BY
            CAST(
                DATEADD(HOUR, -1, hi.pick_at) AS DATE
            )
    ),

    Dates AS (
        SELECT
            record_date
        FROM GridDaily

        UNION

        SELECT
            record_date
        FROM SolarDaily
    )

    SELECT
        d.record_date AS recordDate,

        -- SOLAR
        CAST(
            COALESCE(
                s.solar_value,
                0
            )
            AS DECIMAL(19,2)
        ) AS solarKwh,

        -- GRID giữ nguyên
        CAST(
            COALESCE(
                g.grid_value,
                0
            )
            AS DECIMAL(19,2)
        ) AS gridKwh,

        -- TOTAL = GRID + SOLAR
        CAST(
            COALESCE(
                g.grid_value,
                0
            )
            +
            COALESCE(
                s.solar_value,
                0
            )
            AS DECIMAL(19,2)
        ) AS totalKwh,

        -- SOLAR SHARE = SOLAR / TOTAL
        CAST(
            CASE
                WHEN
                    COALESCE(g.grid_value, 0)
                    +
                    COALESCE(s.solar_value, 0) > 0

                THEN
                    COALESCE(s.solar_value, 0)
                    /
                    NULLIF(
                        COALESCE(g.grid_value, 0)
                        +
                        COALESCE(s.solar_value, 0),
                        0
                    )
                    * 100

                ELSE 0
            END
            AS DECIMAL(10,2)
        ) AS solarSharePercent

    FROM Dates d

    LEFT JOIN GridDaily g
        ON g.record_date = d.record_date

    LEFT JOIN SolarDaily s
        ON s.record_date = d.record_date

    ORDER BY
        d.record_date
    """, nativeQuery = true)
	List<SolarDailyTrendProjection> getSolarDailyTrend(
			@Param("fac") String fac,
			@Param("monthStart") LocalDateTime monthStart,
			@Param("nextMonthStart") LocalDateTime nextMonthStart,
			@Param("energyName") String energyName,
			@Param("excludedMeters") List<String> excludedMeters
	);


	// Solar kWh per hour for the cost impact of /api/solar/detail; priced in
	// Java by ElectricityPricingService (same rules as every electricity API).
	// A row holds the energy of the hour ENDING at pick_at, so hourAt =
	// pick_at hour - 1h; the caller shifts fromTs / toTs by +1h.
	// :excludedMeters = upper-case box_device_id, never empty.
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
			    WHERE pa.name_en = :energyName
			      AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR'
			      AND (
			          UPPER(:fac) = 'KVH'
			          OR UPPER(sc.fac) = UPPER(:fac)
			      )
			      AND UPPER(LTRIM(RTRIM(pa.box_device_id))) NOT IN (:excludedMeters)
			)
			SELECT
			    DATEADD(HOUR, DATEDIFF(HOUR, 0, hm.pick_at) - 1, 0)         AS hourAt,
			    CAST(SUM(CAST(hm.[value] AS DECIMAL(19, 6))) AS DECIMAL(19, 4)) AS kwh
			FROM SolarParam sp
			INNER JOIN dbo.F2_Utility_Para_History_Main hm
			        ON hm.box_device_id = sp.box_device_id
			       AND hm.plc_address = sp.plc_address
			WHERE hm.pick_at >= :fromTs
			  AND hm.pick_at <  :toTs
			  AND hm.[value] > 0
			  AND hm.MTD = 'MTD'
			GROUP BY DATEADD(HOUR, DATEDIFF(HOUR, 0, hm.pick_at) - 1, 0)
			""", nativeQuery = true)
	List<SolarHourEnergyProjection> findSolarHourlyEnergy(
			@Param("fac") String fac,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			@Param("energyName") String energyName,
			@Param("excludedMeters") List<String> excludedMeters
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

			        AND UPPER(LTRIM(RTRIM(pa.box_device_id))) NOT IN (:excludedMeters)
			)

			SELECT
			    DATEPART(
			        HOUR,
			        DATEADD(HOUR, -1, hm.pick_at)
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
			    hm.pick_at >= DATEADD(HOUR, 1, :dayStart)
			    AND hm.pick_at < DATEADD(HOUR, 1, :nextDayStart)
			
			    AND hm.[value] > 0
			
			    AND ISNULL(
			        hm.MTD,
			        ''
			    ) = 'MTD'
			
			GROUP BY
			    DATEPART(
			        HOUR,
			        DATEADD(HOUR, -1, hm.pick_at)
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
			String energyName,

			@Param("excludedMeters")
			List<String> excludedMeters
	);
}