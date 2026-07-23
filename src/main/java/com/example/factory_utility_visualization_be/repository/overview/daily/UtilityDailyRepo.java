package com.example.factory_utility_visualization_be.repository.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailySignalProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityDailyRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
            /* =====================================================
             * ELECTRICITY
             * ===================================================== */
            SELECT
                'ENERGY' AS utilityType,
                CAST(hi.pick_at AS DATE) AS recordDate,

                CAST(
                    SUM(
                        CAST(hi.[value] AS DECIMAL(19, 4))
                    )
                    AS DECIMAL(19, 4)
                ) AS [value]

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
               AND pa.name_en = 'Total Energy Consumption'

            WHERE hi.pick_at >= :fromTime
              AND hi.pick_at < :toTime
              AND hi.[value] > 0

              AND EXISTS (
                  SELECT 1

                  FROM dbo.F2_Utility_Scada_Channel ch

                  INNER JOIN dbo.F2_Utility_Scada sc
                      ON sc.scada_id = ch.scada_id

                  WHERE ch.box_device_id = hi.box_device_id
                    AND (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
              )

            GROUP BY
                CAST(hi.pick_at AS DATE)

            UNION ALL

            /* =====================================================
             * WATER
             * ===================================================== */
            SELECT
                'WATER' AS utilityType,
                CAST(hi.pick_at AS DATE) AS recordDate,

                CAST(
                    AVG(
                        CAST(hi.[value] AS DECIMAL(19, 4))
                    )
                    AS DECIMAL(19, 4)
                ) AS [value]

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
               AND pa.name_en LIKE 'Cooling tank%'

            WHERE hi.pick_at >= :fromTime
              AND hi.pick_at < :toTime
              AND hi.[value] > 0

              AND EXISTS (
                  SELECT 1

                  FROM dbo.F2_Utility_Scada_Channel ch

                  INNER JOIN dbo.F2_Utility_Scada sc
                      ON sc.scada_id = ch.scada_id

                  WHERE ch.box_device_id = hi.box_device_id
                    AND (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
              )

            GROUP BY
                CAST(hi.pick_at AS DATE)

            UNION ALL

            /* =====================================================
             * AIR
             * Fac_A dùng AIR của Fac_B
             * ===================================================== */
            SELECT
                'AIR' AS utilityType,
                CAST(hi.pick_at AS DATE) AS recordDate,

                CAST(
                    AVG(
                        CAST(hi.[value] AS DECIMAL(19, 4))
                    )
                    AS DECIMAL(19, 4)
                ) AS [value]

            FROM dbo.F2_Utility_Para_History_Main hi

            INNER JOIN dbo.F2_Utility_Para pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
               AND pa.name_en =
                   'Sensor compressed air pressure Data'

            WHERE hi.pick_at >= :fromTime
              AND hi.pick_at < :toTime
              AND hi.[value] > 0

              AND EXISTS (
                  SELECT 1

                  FROM dbo.F2_Utility_Scada_Channel ch

                  INNER JOIN dbo.F2_Utility_Scada sc
                      ON sc.scada_id = ch.scada_id

                  WHERE ch.box_device_id = hi.box_device_id

                    AND (
                        :fac = 'KVH'

                        OR (
                            :fac = 'Fac_A'
                            AND sc.fac = 'Fac_B'
                        )

                        OR (
                            :fac <> 'Fac_A'
                            AND sc.fac = :fac
                        )
                    )
              )

            GROUP BY
                CAST(hi.pick_at AS DATE)

            ORDER BY
                recordDate,
                utilityType
            """, nativeQuery = true)
	List<UtilityDailyDashboardProjection>
	getDailyDashboardByMonth(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);

	@Query(value = """
        WITH RawData AS (
            SELECT
                hi.box_device_id,
                hi.plc_address,
                hi.pick_at,

                CAST(
                    hi.[value] AS DECIMAL(19, 4)
                ) AS signal_value,

                COALESCE(
                    NULLIF(LTRIM(RTRIM(pa.name_en)), ''),
                    hi.plc_address
                ) AS name_en,

                COALESCE(
                    NULLIF(LTRIM(RTRIM(pa.unit)), ''),
                    ''
                ) AS unit,

                CAST(hi.pick_at AS DATE) AS record_date,

                ROW_NUMBER() OVER (
                    PARTITION BY
                        hi.box_device_id,
                        hi.plc_address,
                        CAST(hi.pick_at AS DATE)
                    ORDER BY
                        hi.pick_at ASC
                ) AS rn_first,

                ROW_NUMBER() OVER (
                    PARTITION BY
                        hi.box_device_id,
                        hi.plc_address,
                        CAST(hi.pick_at AS DATE)
                    ORDER BY
                        hi.pick_at DESC
                ) AS rn_last

            FROM dbo.F2_Utility_Para_History_Main hi

            LEFT JOIN dbo.F2_Utility_Para pa
                ON LTRIM(RTRIM(pa.box_device_id)) =
                   LTRIM(RTRIM(hi.box_device_id))
               AND LTRIM(RTRIM(pa.plc_address)) =
                   LTRIM(RTRIM(hi.plc_address))

            WHERE LTRIM(RTRIM(hi.box_device_id)) =
                  LTRIM(RTRIM(:boxDeviceId))

              AND hi.pick_at >= :fromTime
              AND hi.pick_at < :toTime
              AND hi.[value] IS NOT NULL
        ),

        DailyData AS (
            SELECT
                box_device_id,
                plc_address,
                name_en,
                unit,
                record_date,

                AVG(signal_value) AS avg_value,
                MIN(signal_value) AS min_value,
                MAX(signal_value) AS max_value,

                /* =================================================
                 * Total Energy Consumption:
                 * tính SUM(value > 0) giống daily dashboard cũ
                 * ================================================= */
                SUM(
                    CASE
                        WHEN UPPER(LTRIM(RTRIM(name_en))) =
                             'TOTAL ENERGY CONSUMPTION'
                         AND signal_value > 0
                        THEN signal_value
                        ELSE 0
                    END
                ) AS energy_sum_value,

                SUM(
                    CASE
                        WHEN UPPER(LTRIM(RTRIM(name_en))) =
                             'TOTAL ENERGY CONSUMPTION'
                         AND signal_value > 0
                        THEN 1
                        ELSE 0
                    END
                ) AS energy_sample_count,

                MAX(
                    CASE
                        WHEN rn_first = 1
                        THEN signal_value
                    END
                ) AS first_value,

                MAX(
                    CASE
                        WHEN rn_last = 1
                        THEN signal_value
                    END
                ) AS last_value,

                COUNT_BIG(*) AS sample_count

            FROM RawData

            GROUP BY
                box_device_id,
                plc_address,
                name_en,
                unit,
                record_date
        )

        SELECT
            box_device_id AS boxDeviceId,
            plc_address AS plcAddress,
            name_en AS nameEn,
            unit AS unit,
            record_date AS recordDate,

            /* =================================================
             * Energy không dùng AVG.
             * Các signal còn lại dùng AVG.
             * ================================================= */
            CAST(
                CASE
                    WHEN UPPER(LTRIM(RTRIM(name_en))) =
                         'TOTAL ENERGY CONSUMPTION'
                    THEN NULL

                    ELSE avg_value
                END
                AS DECIMAL(19, 4)
            ) AS avgValue,

            CAST(
                min_value AS DECIMAL(19, 4)
            ) AS minValue,

            CAST(
                max_value AS DECIMAL(19, 4)
            ) AS maxValue,

            CAST(
                first_value AS DECIMAL(19, 4)
            ) AS firstValue,

            CAST(
                last_value AS DECIMAL(19, 4)
            ) AS lastValue,

            /* =================================================
             * Energy daily = SUM(value)
             * Không còn dùng last - first
             * ================================================= */
            CAST(
                CASE
                    WHEN UPPER(LTRIM(RTRIM(name_en))) =
                         'TOTAL ENERGY CONSUMPTION'
                     AND energy_sample_count > 0
                    THEN energy_sum_value

                    ELSE NULL
                END
                AS DECIMAL(19, 4)
            ) AS consumption,

            sample_count AS sampleCount

        FROM DailyData

        ORDER BY
            record_date,
            name_en,
            plc_address
        """, nativeQuery = true)
	List<UtilityDailySignalProjection> getDailySignals(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);
}