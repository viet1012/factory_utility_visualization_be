package com.example.factory_utility_visualization_be.repository.overview.daily;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.overview.daily.projection.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.projection.UtilityDailyElectricityStackProjection;

@Repository
public interface UtilityDailyRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        WITH DeviceMap AS (
            SELECT
                ch.box_device_id,

                MAX(sc.fac) AS fac,

                MAX(
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
                    END
                ) AS is_solar

            FROM dbo.F2_Utility_Scada_Channel ch

            INNER JOIN dbo.F2_Utility_Scada sc
                ON sc.scada_id = ch.scada_id

            GROUP BY
                ch.box_device_id
        ),

        EnergyRaw AS (
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS record_date,

                hi.box_device_id,

                dm.is_solar,

                CAST(
                    hi.[value]
                    AS DECIMAL(19,6)
                ) AS energy_value

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address

            INNER JOIN DeviceMap dm
                ON dm.box_device_id = hi.box_device_id

            WHERE
                pa.name_en = 'Total Energy Consumption'

                AND hi.pick_at >= :fromTime
                AND hi.pick_at < :toTime

                AND hi.[value] > 0

                AND (
                    UPPER(:fac) = 'KVH'
                    OR UPPER(dm.fac) = UPPER(:fac)
                )
        ),

        DailyEnergy AS (
            SELECT
                record_date,

                -- =========================================
                -- TOTAL
                -- Meter thường đã bao gồm phần Solar
                -- =========================================
                SUM(
                    CASE
                        WHEN is_solar = 0
                        THEN energy_value
                        ELSE 0
                    END
                ) AS total_value,

                -- =========================================
                -- SOLAR hiện tại
                -- SUM tất cả meter có box_id = SOLAR
                -- =========================================
                SUM(
                    CASE
                        WHEN is_solar = 1
                        THEN energy_value
                        ELSE 0
                    END
                ) AS solar_value

            FROM EnergyRaw

            GROUP BY
                record_date
        )

        SELECT
            record_date AS recordDate,

            -- Điện thường: giữ nguyên
            CAST(
                COALESCE(total_value, 0)
                AS DECIMAL(19,4)
            ) AS gridKwh,

            -- Solar: chỉ lấy meter Solar
            CAST(
                COALESCE(solar_value, 0)
                AS DECIMAL(19,4)
            ) AS solarKwh,

            -- Nếu totalKwh muốn thể hiện tổng thực tế:
            -- điện thường + solar
            CAST(
                COALESCE(total_value, 0)
                +
                COALESCE(solar_value, 0)
                AS DECIMAL(19,4)
            ) AS totalKwh

        FROM DailyEnergy

        ORDER BY
            record_date
        """, nativeQuery = true)
	List<UtilityDailyElectricityStackProjection>
	getDailyElectricityByDateRange(

			@Param("fac")
			String fac,

			@Param("fromTime")
			LocalDateTime fromTime,

			@Param("toTime")
			LocalDateTime toTime
	);


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
            CAST(
                hi.pick_at
                AS DATE
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
    )

    SELECT
        'WATER' AS utilityType,

        record_date AS recordDate,

        CAST(
            AVG(value)
            AS DECIMAL(19,4)
        ) AS [value]

    FROM RawData

    WHERE
        name_en LIKE 'Cooling tank%'

        AND (
            UPPER(:fac) = 'KVH'
            OR same_fac = 1
        )

    GROUP BY
        record_date


    UNION ALL


    SELECT
        'AIR' AS utilityType,

        record_date AS recordDate,

        CAST(
            AVG(value)
            AS DECIMAL(19,4)
        ) AS [value]

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
        record_date

    ORDER BY
        recordDate,
        utilityType
    """, nativeQuery = true)
	List<UtilityDailyDashboardProjection> getDailyWaterAndAirByDateRange(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);
}