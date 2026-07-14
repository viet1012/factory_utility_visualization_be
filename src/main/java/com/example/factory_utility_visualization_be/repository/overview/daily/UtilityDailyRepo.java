package com.example.factory_utility_visualization_be.repository.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardProjection;
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
}