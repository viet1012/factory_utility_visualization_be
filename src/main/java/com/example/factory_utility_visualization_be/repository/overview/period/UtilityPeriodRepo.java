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
	// 1. TOTAL TREND THEO NGÀY
	// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
            pa.unit

        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en = 'Total Energy Consumption'
    ),

    ValidDevice AS (
        SELECT DISTINCT
            ch.box_device_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'
                OR UPPER(sc.fac) = UPPER(:fac)
            )

            AND UPPER(
                LTRIM(
                    RTRIM(
                        ISNULL(ch.box_id, '')
                    )
                )
            ) <> 'SOLAR'
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
	// 2. TOTAL THEO TỪNG TỦ
	// ============================================================

	@Query(value = """
        WITH DeviceMap AS (
            SELECT DISTINCT
                ch.box_device_id,
                LTRIM(
                    RTRIM(
                        ISNULL(ch.box_id, '')
                    )
                ) AS box_id

            FROM dbo.F2_Utility_Scada_Channel ch

            INNER JOIN dbo.F2_Utility_Scada sc
                ON sc.scada_id = ch.scada_id

            WHERE
                (
                    UPPER(:fac) = 'KVH'
                    OR UPPER(sc.fac) = UPPER(:fac)
                )

                AND UPPER(
                    LTRIM(
                        RTRIM(
                            ISNULL(ch.box_id, '')
                        )
                    )
                ) <> 'SOLAR'
        )

        SELECT
            hi.box_device_id AS boxDeviceId,

            MAX(dm.box_id) AS boxId,

            CAST(
                SUM(
                    CAST(
                        hi.[value]
                        AS DECIMAL(19, 4)
                    )
                )
                AS DECIMAL(19, 4)
            ) AS [value]

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

        GROUP BY
            hi.box_device_id

        ORDER BY
            [value] DESC
        """, nativeQuery = true)
	List<UtilityPeriodBoxProjection>
	getElectricityByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


	// ============================================================
	// 3. TỪNG TỦ × TỪNG NGÀY
	// ============================================================

	@Query(value = """
        WITH DeviceMap AS (
            SELECT DISTINCT
                ch.box_device_id,

                LTRIM(
                    RTRIM(
                        ISNULL(ch.box_id, '')
                    )
                ) AS box_id

            FROM dbo.F2_Utility_Scada_Channel ch

            INNER JOIN dbo.F2_Utility_Scada sc
                ON sc.scada_id = ch.scada_id

            WHERE
                (
                    UPPER(:fac) = 'KVH'
                    OR UPPER(sc.fac) = UPPER(:fac)
                )

                AND UPPER(
                    LTRIM(
                        RTRIM(
                            ISNULL(ch.box_id, '')
                        )
                    )
                ) <> 'SOLAR'
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
                        AS DECIMAL(19, 4)
                    )
                )
                AS DECIMAL(19, 4)
            ) AS [value]

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

        GROUP BY
            hi.box_device_id,
            CAST(hi.pick_at AS DATE)

        ORDER BY
            hi.box_device_id,
            recordDate
        """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection>
	getElectricityBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);

	// ============================================================
// 4. WATER - TOTAL TREND THEO NGÀY
//
// Daily logic:
// Cooling tank%
// AVG(value)
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
			pa.unit
        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en LIKE 'Cooling tank%'
    ),

    ValidDevice AS (
        SELECT DISTINCT
            ch.box_device_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'
                OR UPPER(sc.fac) = UPPER(:fac)
            )
    )

    SELECT
        CAST(
            hi.pick_at
            AS DATE
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
            hi.pick_at
            AS DATE
        )

    ORDER BY
        recordDate
    """, nativeQuery = true)
	List<UtilityPeriodTrendProjection>
	getWaterTrend(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


// ============================================================
// 5. WATER - THEO TỪNG BOX_ID
//
// 1 box_id có thể có nhiều box_device_id.
// AVG toàn bộ dữ liệu thuộc box đó.
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
            pa.unit

        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en LIKE 'Cooling tank%'
    ),

    DeviceMap AS (
        SELECT DISTINCT
            ch.box_device_id,

            LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) AS box_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'
                OR UPPER(sc.fac) = UPPER(:fac)
            )

            AND LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) <> ''
    )

    SELECT
        CAST(
            NULL
            AS VARCHAR(255)
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
	List<UtilityPeriodBoxProjection>
	getWaterByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


// ============================================================
// 6. WATER - BOX_ID × NGÀY
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
			pa.unit
        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en LIKE 'Cooling tank%'
    ),

    DeviceMap AS (
        SELECT DISTINCT
            ch.box_device_id,

            LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) AS box_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'
                OR UPPER(sc.fac) = UPPER(:fac)
            )

            AND LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) <> ''
    )

    SELECT
        CAST(
            NULL
            AS VARCHAR(255)
        ) AS boxDeviceId,

        dm.box_id AS boxId,

        CAST(
            hi.pick_at
            AS DATE
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
            hi.pick_at
            AS DATE
        )

    ORDER BY
        dm.box_id,
        recordDate
    """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection>
	getWaterBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


// ============================================================
// 7. AIR - TOTAL TREND THEO NGÀY
//
// Daily logic:
// Sensor compressed air pressure Data
// AVG(value)
//
// Fac_A -> lấy AIR Fac_B
// KVH   -> tất cả
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
            pa.unit

        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en =
                'Sensor compressed air pressure Data'
    ),

    ValidDevice AS (
        SELECT DISTINCT
            ch.box_device_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'

                OR (
                    UPPER(:fac) = 'FAC_A'
                    AND UPPER(sc.fac) = 'FAC_B'
                )

                OR (
                    UPPER(:fac) <> 'FAC_A'
                    AND UPPER(sc.fac) = UPPER(:fac)
                )
            )
    )

    SELECT
        CAST(
            hi.pick_at
            AS DATE
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
            hi.pick_at
            AS DATE
        )

    ORDER BY
        recordDate
    """, nativeQuery = true)
	List<UtilityPeriodTrendProjection>
	getAirTrend(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


// ============================================================
// 8. AIR - THEO TỪNG BOX_ID
//
// Fac_A -> lấy AIR Fac_B.
// AVG bar, KHÔNG SUM.
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address

        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en =
                'Sensor compressed air pressure Data'
    ),

    DeviceMap AS (
        SELECT DISTINCT
            ch.box_device_id,

            LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) AS box_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'

                OR (
                    UPPER(:fac) = 'FAC_A'
                    AND UPPER(sc.fac) = 'FAC_B'
                )

                OR (
                    UPPER(:fac) <> 'FAC_A'
                    AND UPPER(sc.fac) = UPPER(:fac)
                )
            )

            AND LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) <> ''
    )

    SELECT
        CAST(
            NULL
            AS VARCHAR(255)
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
	List<UtilityPeriodBoxProjection>
	getAirByBox(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);


// ============================================================
// 9. AIR - BOX_ID × NGÀY
//
// AVG pressure theo box/ngày.
// ============================================================

	@Query(value = """
    WITH ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
			pa.unit
        FROM dbo.F2_Utility_Para pa

        WHERE
            pa.name_en =
                'Sensor compressed air pressure Data'
    ),

    DeviceMap AS (
        SELECT DISTINCT
            ch.box_device_id,

            LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) AS box_id

        FROM dbo.F2_Utility_Scada_Channel ch

        INNER JOIN dbo.F2_Utility_Scada sc
            ON sc.scada_id = ch.scada_id

        WHERE
            (
                UPPER(:fac) = 'KVH'

                OR (
                    UPPER(:fac) = 'FAC_A'
                    AND UPPER(sc.fac) = 'FAC_B'
                )

                OR (
                    UPPER(:fac) <> 'FAC_A'
                    AND UPPER(sc.fac) = UPPER(:fac)
                )
            )

            AND LTRIM(
                RTRIM(
                    ISNULL(
                        ch.box_id,
                        ''
                    )
                )
            ) <> ''
    )

    SELECT
        CAST(
            NULL
            AS VARCHAR(255)
        ) AS boxDeviceId,

        dm.box_id AS boxId,

        CAST(
            hi.pick_at
            AS DATE
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
            hi.pick_at
            AS DATE
        )

    ORDER BY
        dm.box_id,
        recordDate
    """, nativeQuery = true)
	List<UtilityPeriodBoxDailyProjection>
	getAirBoxDaily(
			@Param("fac") String fac,
			@Param("fromTime") LocalDateTime fromTime,
			@Param("toTime") LocalDateTime toTime
	);
}