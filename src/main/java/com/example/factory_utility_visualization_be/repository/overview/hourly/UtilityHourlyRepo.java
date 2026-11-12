package com.example.factory_utility_visualization_be.repository.overview.hourly;

import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlyEnergyProjection;
import com.example.factory_utility_visualization_be.repository.overview.hourly.projection.HourlySensorCompareProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityHourlyRepo
		extends JpaRepository<DummyEntity, Long> {

	// ============================================================
	// 1. ELECTRICITY
	//
	// Grid kWh (no Solar) per day x hour, NO money: UtilityHourlyService
	// prices every hour with ElectricityPricingService (the only place
	// electricity money is computed). A row holds the energy of the hour
	// ENDING at pick_at, so the hour is pick_at - 1h; the caller shifts the
	// window by +1h (same as power-usage / electric-consumption).
	// ============================================================

	@Query(value = """
    WITH DeviceMap AS (
        SELECT
            ch.box_device_id,
            MAX(sc.fac) AS fac,
            MAX(
                CASE
                    WHEN UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR'
                    THEN 1
                    ELSE 0
                END
            ) AS is_solar
        FROM dbo.F2_Utility_Scada_Channel ch
        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id
        GROUP BY
            ch.box_device_id
    )

    SELECT
        CAST(CAST(DATEADD(HOUR, -1, hi.pick_at) AS DATE) AS DATETIME) AS recordDate,
        DATEPART(HOUR, DATEADD(HOUR, -1, hi.pick_at))                  AS hourNumber,
        CAST(SUM(CAST(hi.[value] AS DECIMAL(19,6))) AS DECIMAL(19,6))  AS kwh

    FROM dbo.F2_Utility_Para_History_Main hi

    INNER JOIN dbo.F2_Utility_Para pa
        ON pa.box_device_id = hi.box_device_id
       AND pa.plc_address = hi.plc_address

    INNER JOIN DeviceMap dm
        ON dm.box_device_id = hi.box_device_id

    WHERE
        hi.pick_at >= :fromTime
        AND hi.pick_at < :toTime

        AND hi.[value] > 0

        AND pa.name_en = :nameEn

        -- GRID / điện thường
        AND dm.is_solar = 0

        AND (
            UPPER(:fac) = 'KVH'
            OR UPPER(dm.fac) = UPPER(:fac)
        )

        AND UPPER(LTRIM(RTRIM(dm.box_device_id))) NOT IN (:excludedMeters)

        AND hi.MTD = 'MTD'

    GROUP BY
        CAST(DATEADD(HOUR, -1, hi.pick_at) AS DATE),
        DATEPART(HOUR, DATEADD(HOUR, -1, hi.pick_at))

    ORDER BY
        recordDate,
        hourNumber
    """, nativeQuery = true)
	List<HourlyEnergyProjection> findHourlyElectricity(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime,
			@Param("nameEn") String nameEn,
			// upper-case box_device_id, never empty
			@Param("excludedMeters") List<String> excludedMeters
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