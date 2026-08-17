package com.example.factory_utility_visualization_be.repository.overview.period;

import com.example.factory_utility_visualization_be.dto.overview.period.projection.*;
import com.example.factory_utility_visualization_be.model.DummyEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityPeriodRepo
		extends JpaRepository<DummyEntity, Long> {

	// ============================================================
	// 1. ELECTRICITY - TOTAL TREND THEO NGÀY
	//
	// - Total Energy Consumption
	// - Không lấy SOLAR
	// - SUM theo ngày
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address,
                    MAX(pa.unit) AS unit
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en = 'Total Energy Consumption'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            ValidDevice AS (
                SELECT
                    ch.box_device_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
                    AND ch.box_id <> 'SOLAR'
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    SUM(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value],
            
                MAX(pa.unit) AS unit
            
            FROM dbo.F2_Utility_Para_History_Main hi
            
            INNER JOIN ParaDedup pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
            
            INNER JOIN ValidDevice vd
                ON vd.box_device_id = hi.box_device_id
            
            WHERE
                hi.pick_at >= :fromTime
                AND hi.pick_at < :toTime
            
                AND hi.[value] > 0
            
            GROUP BY
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodTrendProjection> getElectricityTrend(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 2. ELECTRICITY - TOTAL THEO TỪNG PANEL
	//
	// - Không lấy SOLAR
	// - SUM theo box_device_id
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en = 'Total Energy Consumption'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
                    AND ch.box_id <> 'SOLAR'
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                hi.box_device_id AS boxDeviceId,
            
                MAX(dm.box_id) AS boxId,
            
                CAST(
                    SUM(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value]
            
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
            
            GROUP BY
                hi.box_device_id
            
            ORDER BY
                [value] DESC
            """, nativeQuery = true)
	List<UtilityPeriodBoxProjection> getElectricityByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 3. ELECTRICITY - PANEL × NGÀY
	//
	// Heatmap
	// - Không lấy SOLAR
	// - SUM từng device/ngày
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en = 'Total Energy Consumption'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
                    AND ch.box_id <> 'SOLAR'
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                hi.box_device_id AS boxDeviceId,
            
                MAX(dm.box_id) AS boxId,
            
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    SUM(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value]
            
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
            
            GROUP BY
                hi.box_device_id,
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                hi.box_device_id,
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection> getElectricityBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 4. WATER - TOTAL TREND THEO NGÀY
	//
	// Cooling tank%
	// AVG(value)
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address,
                    MAX(pa.unit) AS unit
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en LIKE 'Cooling tank%'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            ValidDevice AS (
                SELECT
                    ch.box_device_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value],
            
                MAX(pa.unit) AS unit
            
            FROM dbo.F2_Utility_Para_History_Main hi
            
            INNER JOIN ParaDedup pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
            
            INNER JOIN ValidDevice vd
                ON vd.box_device_id = hi.box_device_id
            
            WHERE
                hi.pick_at >= :fromTime
                AND hi.pick_at < :toTime
            
                AND hi.[value] > 0
            
            GROUP BY
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodTrendProjection> getWaterTrend(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 5. WATER - THEO TỪNG PANEL
	//
	// Một box_id có thể chứa nhiều box_device_id.
	//
	// AVG toàn bộ sensor thuộc panel.
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address,
                    MAX(pa.unit) AS unit
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en LIKE 'Cooling tank%'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    NULL AS VARCHAR(255)
                ) AS boxDeviceId,
            
                dm.box_id AS boxId,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value],
            
                MAX(pa.unit) AS unit
            
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
            
            GROUP BY
                dm.box_id
            
            ORDER BY
                [value] DESC
            """, nativeQuery = true)
	List<UtilityPeriodBoxProjection> getWaterByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 6. WATER - PANEL × NGÀY
	//
	// Heatmap
	// AVG theo box/ngày
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en LIKE 'Cooling tank%'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
                        :fac = 'KVH'
                        OR sc.fac = :fac
                    )
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    NULL AS VARCHAR(255)
                ) AS boxDeviceId,
            
                dm.box_id AS boxId,
            
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value]
            
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
            
            GROUP BY
                dm.box_id,
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                dm.box_id,
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection> getWaterBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 7. AIR - TOTAL TREND THEO NGÀY
	//
	// Sensor compressed air pressure Data
	//
	// AVG(value)
	//
	// Special mapping:
	//
	// Fac_A -> lấy AIR Fac_B
	// Fac_B -> Fac_B
	// Fac_C -> Fac_C
	// KVH   -> tất cả
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address,
                    MAX(pa.unit) AS unit
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en =
                    'Sensor compressed air pressure Data'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            ValidDevice AS (
                SELECT
                    ch.box_device_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
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
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value],
            
                MAX(pa.unit) AS unit
            
            FROM dbo.F2_Utility_Para_History_Main hi
            
            INNER JOIN ParaDedup pa
                ON pa.box_device_id = hi.box_device_id
               AND pa.plc_address = hi.plc_address
            
            INNER JOIN ValidDevice vd
                ON vd.box_device_id = hi.box_device_id
            
            WHERE
                hi.pick_at >= :fromTime
                AND hi.pick_at < :toTime
            
                AND hi.[value] > 0
            
            GROUP BY
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodTrendProjection> getAirTrend(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 8. AIR - THEO TỪNG PANEL
	//
	// AVG pressure theo panel
	//
	// Fac_A -> lấy AIR Fac_B
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en =
                    'Sensor compressed air pressure Data'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
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
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    NULL AS VARCHAR(255)
                ) AS boxDeviceId,
            
                dm.box_id AS boxId,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value]
            
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
            
            GROUP BY
                dm.box_id
            
            ORDER BY
                [value] DESC
            """, nativeQuery = true)
	List<UtilityPeriodBoxProjection> getAirByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 9. AIR - PANEL × NGÀY
	//
	// Heatmap
	//
	// AVG pressure theo box/ngày
	//
	// Fac_A -> lấy AIR Fac_B
	// ============================================================

	@Query(value = """
            WITH ParaDedup AS (
                SELECT
                    pa.box_device_id,
                    pa.plc_address
            
                FROM dbo.F2_Utility_Para pa
            
                WHERE
                    pa.name_en =
                    'Sensor compressed air pressure Data'
            
                GROUP BY
                    pa.box_device_id,
                    pa.plc_address
            ),
            
            DeviceMap AS (
                SELECT
                    ch.box_device_id,
                    MAX(ch.box_id) AS box_id
            
                FROM dbo.F2_Utility_Scada_Channel ch
            
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
            
                WHERE
                    (
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
            
                    AND ch.box_id IS NOT NULL
                    AND ch.box_id <> ''
            
                GROUP BY
                    ch.box_device_id
            )
            
            SELECT
                CAST(
                    NULL AS VARCHAR(255)
                ) AS boxDeviceId,
            
                dm.box_id AS boxId,
            
                CAST(
                    hi.pick_at AS DATE
                ) AS recordDate,
            
                CAST(
                    AVG(
                        CAST(
                            hi.[value]
                            AS DECIMAL(19,4)
                        )
                    )
                    AS DECIMAL(19,4)
                ) AS [value]
            
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
            
            GROUP BY
                dm.box_id,
                CAST(
                    hi.pick_at AS DATE
                )
            
            ORDER BY
                dm.box_id,
                recordDate
            """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection> getAirBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);
}