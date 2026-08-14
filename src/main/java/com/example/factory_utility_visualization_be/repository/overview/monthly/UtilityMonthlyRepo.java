package com.example.factory_utility_visualization_be.repository.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UtilityMonthlyRepo extends JpaRepository<DummyEntity, Long> {
	// =========================================================
	// FAC_A / FAC_B / FAC_C
	//
	// BUSINESS LOGIC:
	//
	// grossValue = meter electricity hiện tại
	// solarValue = điện Solar
	//
	// gridValue = grossValue - solarValue
	//
	// value trả ra API = gridValue
	//
	// Lưu ý:
	// - TargetPara vẫn loại box_id = SOLAR.
	// - Vì meter điện chính vẫn đã bao gồm phần Solar,
	//   nên phải trừ Solar thêm một lần ở EnergyNetMonthly.
	// =========================================================

	@Query(value = """
			WITH TargetPara AS (
			    SELECT DISTINCT
			        sc.fac,
			        pa.name_en,

			        CASE
			            WHEN pa.name_en = 'Total Energy Consumption'
			                THEN 'Electricity'

			            WHEN pa.name_en LIKE '%Cooling tank%'
			                THEN 'Water'

			            WHEN pa.name_en = 'Data Pipeline pressure'
			                THEN 'Water'

			            WHEN pa.name_en =
			                 'Sensor compressed air pressure Data'
			                THEN 'Compressed Air'

			            ELSE ch.cate
			        END AS cate,

			        pa.unit,
			        pa.box_device_id,
			        pa.plc_address

			    FROM dbo.F2_Utility_Para pa

			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id

			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id

			    WHERE
			        UPPER(
			            LTRIM(
			                RTRIM(
			                    ISNULL(ch.box_id, '')
			                )
			            )
			        ) <> 'SOLAR'

			        AND (
			            (
			                UPPER(sc.fac) = UPPER(:fac)

			                AND (
			                       pa.name_en =
			                           'Total Energy Consumption'

			                    OR pa.name_en LIKE
			                       '%Cooling tank%'

			                    OR pa.name_en =
			                       'Data Pipeline pressure'

			                    OR pa.name_en =
			                       'Sensor compressed air pressure Data'
			                )
			            )

			            OR

			            (
			                UPPER(:fac) = 'FAC_A'

			                AND UPPER(sc.fac) = 'FAC_B'

			                AND pa.name_en =
			                    'Sensor compressed air pressure Data'
			            )
			        )
			),

			-- =====================================================
			-- SOLAR DEVICES
			-- =====================================================

			SolarTargetPara AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address

			    FROM dbo.F2_Utility_Para pa

			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id =
			           pa.box_device_id

			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id =
			           ch.scada_id

			    WHERE
			        pa.name_en =
			            'Total Energy Consumption'

			        AND UPPER(
			            LTRIM(
			                RTRIM(
			                    ISNULL(ch.box_id, '')
			                )
			            )
			        ) = 'SOLAR'

			        AND UPPER(sc.fac) =
			            UPPER(:fac)
			),

			-- =====================================================
			-- SOLAR RAW
			-- =====================================================

			SolarBase AS (
			    SELECT
			        'CURRENT' AS period_type,

			        hm.pick_at,

			        CAST(
			            hm.[value]
			            AS DECIMAL(19, 6)
			        ) AS solar_value,

			        DATEPART(
			            HOUR,
			            hm.pick_at
			        ) AS HourNumber,

			        CASE
			            WHEN DATEPART(
			                WEEKDAY,
			                CAST(hm.pick_at AS DATE)
			            ) = 1
			                THEN '1'
			            ELSE '2-7'
			        END AS WD

			    FROM SolarTargetPara stp

			    INNER JOIN
			        dbo.F2_Utility_Para_History_Main hm

			        ON hm.box_device_id =
			           stp.box_device_id

			       AND hm.plc_address =
			           stp.plc_address

			    WHERE
			        hm.pick_at >= :from

			        AND hm.pick_at < :currentTo

			        AND hm.[value] > 0

			        AND ISNULL(
			            hm.MTD,
			            ''
			        ) = 'MTD'

			    UNION ALL

			    SELECT
			        'PREV' AS period_type,

			        hm.pick_at,

			        CAST(
			            hm.[value]
			            AS DECIMAL(19, 6)
			        ) AS solar_value,

			        DATEPART(
			            HOUR,
			            hm.pick_at
			        ) AS HourNumber,

			        CASE
			            WHEN DATEPART(
			                WEEKDAY,
			                CAST(hm.pick_at AS DATE)
			            ) = 1
			                THEN '1'
			            ELSE '2-7'
			        END AS WD

			    FROM SolarTargetPara stp

			    INNER JOIN
			        dbo.F2_Utility_Para_History_Main hm

			        ON hm.box_device_id =
			           stp.box_device_id

			       AND hm.plc_address =
			           stp.plc_address

			    WHERE
			        hm.pick_at >= :prevFrom

			        AND hm.pick_at < :prevTo

			        AND hm.[value] > 0

			        AND ISNULL(
			            hm.MTD,
			            ''
			        ) = 'MTD'
			),

			-- =====================================================
			-- SOLAR THEO KHUNG GIỜ
			--
			-- Cần CTE này để tiền điện Solar được trừ đúng tariff.
			-- =====================================================

			SolarHourly AS (
			    SELECT
			        period_type,
			        WD,
			        HourNumber,

			        SUM(solar_value)
			            AS solar_hour_value

			    FROM SolarBase

			    GROUP BY
			        period_type,
			        WD,
			        HourNumber
			),

			-- =====================================================
			-- SOLAR MONTHLY TOTAL
			-- =====================================================

			SolarMonthly AS (
			    SELECT
			        CAST(
			            COALESCE(
			                SUM(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                            THEN solar_value
			                        ELSE 0
			                    END
			                ),
			                0
			            )
			            AS DECIMAL(18, 2)
			        ) AS solarValue,

			        CAST(
			            COALESCE(
			                SUM(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                            THEN solar_value
			                        ELSE 0
			                    END
			                ),
			                0
			            )
			            AS DECIMAL(18, 2)
			        ) AS prevSolarValue

			    FROM SolarBase
			),

			-- =====================================================
			-- NORMAL UTILITY DATA
			-- =====================================================

			Base AS (
			    SELECT
			        'CURRENT' AS period_type,

			        tp.fac,
			        tp.name_en,
			        tp.cate,
			        tp.unit,

			        hi.pick_at,
			        hi.[value],

			        DATEPART(
			            HOUR,
			            hi.pick_at
			        ) AS HourNumber,

			        CASE
			            WHEN DATEPART(
			                WEEKDAY,
			                CAST(hi.pick_at AS DATE)
			            ) = 1
			                THEN '1'
			            ELSE '2-7'
			        END AS WD

			    FROM TargetPara tp

			    INNER JOIN
			        dbo.F2_Utility_Para_History_Main hi

			        ON hi.box_device_id =
			           tp.box_device_id

			       AND hi.plc_address =
			           tp.plc_address

			       AND hi.pick_at >= :from

			       AND hi.pick_at < :currentTo

			       AND hi.[value] > 0

			    UNION ALL

			    SELECT
			        'PREV' AS period_type,

			        tp.fac,
			        tp.name_en,
			        tp.cate,
			        tp.unit,

			        hi.pick_at,
			        hi.[value],

			        DATEPART(
			            HOUR,
			            hi.pick_at
			        ) AS HourNumber,

			        CASE
			            WHEN DATEPART(
			                WEEKDAY,
			                CAST(hi.pick_at AS DATE)
			            ) = 1
			                THEN '1'
			            ELSE '2-7'
			        END AS WD

			    FROM TargetPara tp

			    INNER JOIN
			        dbo.F2_Utility_Para_History_Main hi

			        ON hi.box_device_id =
			           tp.box_device_id

			       AND hi.plc_address =
			           tp.plc_address

			       AND hi.pick_at >= :prevFrom

			       AND hi.pick_at < :prevTo

			       AND hi.[value] > 0
			),

			-- =====================================================
			-- 24 HOURS
			-- =====================================================

			Hours AS (
			    SELECT v.n

			    FROM (
			        VALUES
			            (0),(1),(2),(3),(4),(5),
			            (6),(7),(8),(9),(10),(11),
			            (12),(13),(14),(15),(16),(17),
			            (18),(19),(20),(21),(22),(23)
			    ) v(n)
			),

			-- =====================================================
			-- ELECTRICITY RATE
			-- =====================================================

			HourCost AS (
			    SELECT
			        c.WD,

			        h.n AS HourNumber,

			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime
			                THEN
			                    (
			                        CASE
			                            WHEN c.toTime < h.n + 1
			                                THEN c.toTime
			                            ELSE h.n + 1
			                        END
			                    )
			                    -
			                    (
			                        CASE
			                            WHEN c.frTime > h.n
			                                THEN c.frTime
			                            ELSE h.n
			                        END
			                    )

			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime)
			                        THEN
			                            (
			                                CASE
			                                    WHEN 24.0 < h.n + 1
			                                        THEN 24.0
			                                    ELSE h.n + 1
			                                END
			                            )
			                            -
			                            (
			                                CASE
			                                    WHEN c.frTime > h.n
			                                        THEN c.frTime
			                                    ELSE h.n
			                                END
			                            )

			                        ELSE
			                            (
			                                CASE
			                                    WHEN c.toTime < h.n + 1
			                                        THEN c.toTime
			                                    ELSE h.n + 1
			                                END
			                            )
			                            -
			                            (
			                                CASE
			                                    WHEN 0.0 > h.n
			                                        THEN 0.0
			                                    ELSE h.n
			                                END
			                            )
			                    END

			            END * c.vnd
			        ) AS weighted_vnd_sum,

			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime
			                THEN
			                    (
			                        CASE
			                            WHEN c.toTime < h.n + 1
			                                THEN c.toTime
			                            ELSE h.n + 1
			                        END
			                    )
			                    -
			                    (
			                        CASE
			                            WHEN c.frTime > h.n
			                                THEN c.frTime
			                            ELSE h.n
			                        END
			                    )

			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime)
			                        THEN
			                            (
			                                CASE
			                                    WHEN 24.0 < h.n + 1
			                                        THEN 24.0
			                                    ELSE h.n + 1
			                                END
			                            )
			                            -
			                            (
			                                CASE
			                                    WHEN c.frTime > h.n
			                                        THEN c.frTime
			                                    ELSE h.n
			                                END
			                            )

			                        ELSE
			                            (
			                                CASE
			                                    WHEN c.toTime < h.n + 1
			                                        THEN c.toTime
			                                    ELSE h.n + 1
			                                END
			                            )
			                            -
			                            (
			                                CASE
			                                    WHEN 0.0 > h.n
			                                        THEN 0.0
			                                    ELSE h.n
			                                END
			                            )
			                    END
			            END
			        ) AS total_hours

			    FROM dbo.F2_Utility_Cost_Master c

			    CROSS JOIN Hours h

			    WHERE
			        (
			            c.frTime < c.toTime

			            AND h.n < c.toTime

			            AND h.n + 1 > c.frTime
			        )

			        OR

			        (
			            c.frTime > c.toTime

			            AND (
			                h.n + 1 > c.frTime

			                OR h.n < c.toTime
			            )
			        )

			    GROUP BY
			        c.WD,
			        h.n
			),

			FinalRate AS (
			    SELECT
			        WD,
			        HourNumber,

			        weighted_vnd_sum
			        /
			        NULLIF(
			            total_hours,
			            0
			        ) AS vnd_rate

			    FROM HourCost
			),

			-- =====================================================
			-- GROSS ELECTRICITY BY HOUR
			-- =====================================================

			EnergyHourly AS (
			    SELECT
			        period_type,
			        name_en,
			        cate,
			        unit,
			        WD,
			        HourNumber,

			        SUM([value]) AS hour_value

			    FROM Base

			    WHERE
			        name_en =
			            'Total Energy Consumption'

			    GROUP BY
			        period_type,
			        name_en,
			        cate,
			        unit,
			        WD,
			        HourNumber
			),

			-- =====================================================
			-- MONTHLY GROSS ELECTRICITY
			--
			-- gross_value vẫn là meter chính.
			-- Chưa trừ Solar ở đây.
			-- =====================================================

			EnergyMonthly AS (
			    SELECT
			        e.name_en AS name,
			        e.cate,
			        e.unit,

			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'CURRENT'
			                    THEN e.hour_value
			            END
			        ) AS gross_value,

			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'PREV'
			                    THEN e.hour_value
			            END
			        ) AS gross_prev_value,

			        -- Gross cost
			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'CURRENT'
			                    THEN
			                        e.hour_value
			                        * r.vnd_rate
			            END
			        ) AS gross_vndCost,

			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'PREV'
			                    THEN
			                        e.hour_value
			                        * r.vnd_rate
			            END
			        ) AS gross_prevVndCost,

			        -- Solar cost theo đúng từng tariff hour
			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'CURRENT'
			                    THEN
			                        COALESCE(
			                            sh.solar_hour_value,
			                            0
			                        )
			                        * r.vnd_rate
			                ELSE 0
			            END
			        ) AS solar_vndCost,

			        SUM(
			            CASE
			                WHEN e.period_type =
			                     'PREV'
			                    THEN
			                        COALESCE(
			                            sh.solar_hour_value,
			                            0
			                        )
			                        * r.vnd_rate
			                ELSE 0
			            END
			        ) AS prev_solar_vndCost

			    FROM EnergyHourly e

			    INNER JOIN FinalRate r
			        ON r.WD = e.WD

			       AND r.HourNumber =
			           e.HourNumber

			    LEFT JOIN SolarHourly sh
			        ON sh.period_type =
			           e.period_type

			       AND sh.WD =
			           e.WD

			       AND sh.HourNumber =
			           e.HourNumber

			    GROUP BY
			        e.name_en,
			        e.cate,
			        e.unit
			),

			-- =====================================================
			-- NET / GRID ELECTRICITY
			--
			-- grid = gross - solar
			-- =====================================================

			EnergyNetMonthly AS (
			    SELECT
			        e.name,
			        e.cate,
			        e.unit,

			        CAST(
			            CASE
			                WHEN
			                    COALESCE(
			                        e.gross_value,
			                        0
			                    )
			                    >=
			                    COALESCE(
			                        s.solarValue,
			                        0
			                    )
			                THEN
			                    COALESCE(
			                        e.gross_value,
			                        0
			                    )
			                    -
			                    COALESCE(
			                        s.solarValue,
			                        0
			                    )

			                ELSE 0
			            END
			            AS DECIMAL(18, 2)
			        ) AS value,

			        CAST(
			            CASE
			                WHEN
			                    COALESCE(
			                        e.gross_prev_value,
			                        0
			                    )
			                    >=
			                    COALESCE(
			                        s.prevSolarValue,
			                        0
			                    )
			                THEN
			                    COALESCE(
			                        e.gross_prev_value,
			                        0
			                    )
			                    -
			                    COALESCE(
			                        s.prevSolarValue,
			                        0
			                    )

			                ELSE 0
			            END
			            AS DECIMAL(18, 2)
			        ) AS prevValue,

			        CAST(
			            e.gross_value
			            AS DECIMAL(18, 2)
			        ) AS totalEnergyValue,

			        CAST(
			            e.gross_prev_value
			            AS DECIMAL(18, 2)
			        ) AS prevTotalEnergyValue,

			        s.solarValue,
			        s.prevSolarValue,

			        -- Net VND Cost
			        CAST(
			            CASE
			                WHEN
			                    COALESCE(
			                        e.gross_vndCost,
			                        0
			                    )
			                    >=
			                    COALESCE(
			                        e.solar_vndCost,
			                        0
			                    )
			                THEN
			                    COALESCE(
			                        e.gross_vndCost,
			                        0
			                    )
			                    -
			                    COALESCE(
			                        e.solar_vndCost,
			                        0
			                    )

			                ELSE 0
			            END
			            AS DECIMAL(19, 4)
			        ) AS vndCost,

			        CAST(
			            CASE
			                WHEN
			                    COALESCE(
			                        e.gross_prevVndCost,
			                        0
			                    )
			                    >=
			                    COALESCE(
			                        e.prev_solar_vndCost,
			                        0
			                    )
			                THEN
			                    COALESCE(
			                        e.gross_prevVndCost,
			                        0
			                    )
			                    -
			                    COALESCE(
			                        e.prev_solar_vndCost,
			                        0
			                    )

			                ELSE 0
			            END
			            AS DECIMAL(19, 4)
			        ) AS prevVndCost,

			        -- USD
			        CAST(
			            (
			                CASE
			                    WHEN
			                        COALESCE(
			                            e.gross_vndCost,
			                            0
			                        )
			                        >=
			                        COALESCE(
			                            e.solar_vndCost,
			                            0
			                        )
			                    THEN
			                        COALESCE(
			                            e.gross_vndCost,
			                            0
			                        )
			                        -
			                        COALESCE(
			                            e.solar_vndCost,
			                            0
			                        )
			                    ELSE 0
			                END
			            )
			            /
			            NULLIF(
			                :exchange,
			                0
			            )
			            * :sepzone

			            AS DECIMAL(19, 4)
			        ) AS usdCost,

			        CAST(
			            (
			                CASE
			                    WHEN
			                        COALESCE(
			                            e.gross_prevVndCost,
			                            0
			                        )
			                        >=
			                        COALESCE(
			                            e.prev_solar_vndCost,
			                            0
			                        )
			                    THEN
			                        COALESCE(
			                            e.gross_prevVndCost,
			                            0
			                        )
			                        -
			                        COALESCE(
			                            e.prev_solar_vndCost,
			                            0
			                        )
			                    ELSE 0
			                END
			            )
			            /
			            NULLIF(
			                :exchange,
			                0
			            )
			            * :sepzone

			            AS DECIMAL(19, 4)
			        ) AS prevUsdCost

			    FROM EnergyMonthly e

			    CROSS JOIN SolarMonthly s
			),

			-- =====================================================
			-- WATER TEMPERATURE
			-- =====================================================

			WaterMonthly AS (
			    SELECT
			        'Cooling Tank Temperature' AS name,
			        'Water' AS cate,

			        MAX(unit) AS unit,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS avgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS minValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS maxValue,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevAvgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMinValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMaxValue

			    FROM Base

			    WHERE
			        name_en LIKE
			            '%Cooling tank%'
			),

			-- =====================================================
			-- WATER PIPELINE PRESSURE
			-- =====================================================

			PipelinePressureMonthly AS (
			    SELECT
			        'Data Pipeline pressure' AS name,
			        'Water' AS cate,

			        MAX(unit) AS unit,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS avgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS minValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS maxValue,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevAvgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMinValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMaxValue

			    FROM Base

			    WHERE
			        name_en =
			            'Data Pipeline pressure'
			),

			-- =====================================================
			-- COMPRESSED AIR
			-- =====================================================

			AirMonthly AS (
			    SELECT
			        'Sensor compressed air pressure Data'
			            AS name,

			        'Compressed Air' AS cate,

			        MAX(unit) AS unit,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS avgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS minValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'CURRENT'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS maxValue,

			        CAST(
			            ROUND(
			                AVG(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevAvgValue,

			        CAST(
			            ROUND(
			                MIN(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMinValue,

			        CAST(
			            ROUND(
			                MAX(
			                    CASE
			                        WHEN period_type =
			                             'PREV'
			                        THEN CAST(
			                            [value]
			                            AS DECIMAL(18,4)
			                        )
			                    END
			                ),
			                1
			            )
			            AS DECIMAL(18,1)
			        ) AS prevMaxValue

			    FROM Base

			    WHERE
			        name_en =
			            'Sensor compressed air pressure Data'
			),

			-- =====================================================
			-- LAST PICK
			-- =====================================================

			LastPick AS (
			    SELECT
			        MAX(pick_at) AS pickAt

			    FROM Base

			    WHERE
			        period_type = 'CURRENT'
			),

			-- =====================================================
			-- UNION RESULTS
			-- =====================================================

			FinalRows AS (
			    SELECT
			        e.name,
			        e.cate,
			        e.unit,

			        CAST(NULL AS DECIMAL(18,1))
			            AS minValue,

			        CAST(NULL AS DECIMAL(18,1))
			            AS maxValue,

			        CAST(NULL AS DECIMAL(18,1))
			            AS prevMinValue,

			        CAST(NULL AS DECIMAL(18,1))
			            AS prevMaxValue,

			        CAST(
			            e.value
			            AS DECIMAL(18,2)
			        ) AS value,

			        CAST(NULL AS DECIMAL(18,1))
			            AS avgValue,

			        CAST(
			            e.vndCost
			            AS DECIMAL(18,2)
			        ) AS vndCost,

			        CAST(
			            e.usdCost
			            AS DECIMAL(18,2)
			        ) AS usdCost,

			        CAST(
			            e.prevValue
			            AS DECIMAL(18,2)
			        ) AS prevValue,

			        CAST(NULL AS DECIMAL(18,1))
			            AS prevAvgValue,

			        CAST(
			            e.prevVndCost
			            AS DECIMAL(18,2)
			        ) AS prevVndCost,

			        CAST(
			            e.prevUsdCost
			            AS DECIMAL(18,2)
			        ) AS prevUsdCost

			    FROM EnergyNetMonthly e

			    UNION ALL

			    SELECT
			        w.name,
			        w.cate,
			        w.unit,

			        w.minValue,
			        w.maxValue,

			        w.prevMinValue,
			        w.prevMaxValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        w.avgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2)),

			        CAST(NULL AS DECIMAL(18,2)),
			        w.prevAvgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2))

			    FROM WaterMonthly w

			    UNION ALL

			    SELECT
			        p.name,
			        p.cate,
			        p.unit,

			        p.minValue,
			        p.maxValue,

			        p.prevMinValue,
			        p.prevMaxValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        p.avgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2)),

			        CAST(NULL AS DECIMAL(18,2)),
			        p.prevAvgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2))

			    FROM PipelinePressureMonthly p

			    UNION ALL

			    SELECT
			        a.name,
			        a.cate,
			        a.unit,

			        a.minValue,
			        a.maxValue,

			        a.prevMinValue,
			        a.prevMaxValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        a.avgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2)),

			        CAST(NULL AS DECIMAL(18,2)),
			        a.prevAvgValue,

			        CAST(NULL AS DECIMAL(18,2)),
			        CAST(NULL AS DECIMAL(18,2))

			    FROM AirMonthly a
			)

			-- =====================================================
			-- FINAL
			-- =====================================================

			SELECT
			    f.name AS name,
			    f.cate AS cate,
			    f.unit AS unit,

			    :month AS month,

			    f.minValue AS minValue,
			    f.maxValue AS maxValue,

			    f.prevMinValue AS prevMinValue,
			    f.prevMaxValue AS prevMaxValue,

			    f.value AS value,
			    f.avgValue AS avgValue,

			    f.vndCost AS vndCost,
			    f.usdCost AS usdCost,

			    f.prevValue AS prevValue,
			    f.prevAvgValue AS prevAvgValue,

			    f.prevVndCost AS prevVndCost,
			    f.prevUsdCost AS prevUsdCost,

			    CAST(
			        COALESCE(
			            f.value,
			            f.avgValue,
			            0
			        )
			        -
			        COALESCE(
			            f.prevValue,
			            f.prevAvgValue,
			            0
			        )
			        AS DECIMAL(18,2)
			    ) AS deltaValue,

			    CAST(
			        CASE
			            WHEN
			                COALESCE(
			                    f.prevValue,
			                    f.prevAvgValue,
			                    0
			                ) = 0
			            THEN NULL

			            ELSE
			                (
			                    (
			                        COALESCE(
			                            f.value,
			                            f.avgValue,
			                            0
			                        )
			                        -
			                        COALESCE(
			                            f.prevValue,
			                            f.prevAvgValue,
			                            0
			                        )
			                    )
			                    /
			                    COALESCE(
			                        f.prevValue,
			                        f.prevAvgValue,
			                        0
			                    )
			                ) * 100
			        END

			        AS DECIMAL(10,2)
			    ) AS deltaPercent,

			    lp.pickAt AS pickAt,

			    -- Solar
			    CASE
			        WHEN f.cate = 'Electricity'
			            THEN sm.solarValue
			    END AS solarValue,

			    CASE
			        WHEN f.cate = 'Electricity'
			            THEN sm.prevSolarValue
			    END AS prevSolarValue,

			    -- grossValue trước khi trừ Solar
			    CASE
			        WHEN f.cate = 'Electricity'
			        THEN CAST(
			            COALESCE(f.value, 0)
			            +
			            COALESCE(sm.solarValue, 0)
			            AS DECIMAL(18,2)
			        )
			    END AS totalEnergyValue,

			    CASE
			        WHEN f.cate = 'Electricity'
			        THEN CAST(
			            COALESCE(f.prevValue, 0)
			            +
			            COALESCE(
			                sm.prevSolarValue,
			                0
			            )
			            AS DECIMAL(18,2)
			        )
			    END AS prevTotalEnergyValue,

			    CASE
			        WHEN f.cate = 'Electricity'

			             AND (
			                COALESCE(f.value, 0)
			                +
			                COALESCE(
			                    sm.solarValue,
			                    0
			                )
			             ) > 0

			        THEN CAST(
			            COALESCE(
			                sm.solarValue,
			                0
			            )
			            /
			            NULLIF(
			                COALESCE(f.value, 0)
			                +
			                COALESCE(
			                    sm.solarValue,
			                    0
			                ),
			                0
			            )
			            * 100

			            AS DECIMAL(10,2)
			        )
			    END AS solarSharePercent

			FROM FinalRows f

			CROSS JOIN LastPick lp

			CROSS JOIN SolarMonthly sm

			ORDER BY
			    CASE
			        WHEN f.cate = 'Electricity'
			            THEN 1

			        WHEN f.cate = 'Water'
			             AND f.name =
			                 'Cooling Tank Temperature'
			            THEN 2

			        WHEN f.cate = 'Water'
			             AND f.name =
			                 'Data Pipeline pressure'
			            THEN 3

			        WHEN f.cate =
			             'Compressed Air'
			            THEN 4

			        ELSE 9
			    END

			OPTION (RECOMPILE)
			""", nativeQuery = true)
	List<MonthlySummaryProjection> sumMonthlyByFacRaw(
			@Param("fac") String fac,
			@Param("month") String month,
			@Param("from") LocalDateTime from,
			@Param("currentTo") LocalDateTime currentTo,
			@Param("prevFrom") LocalDateTime prevFrom,
			@Param("prevTo") LocalDateTime prevTo,
			@Param("exchange") BigDecimal exchange,
			@Param("sepzone") BigDecimal sepzone
	);



	@Query(
			value = """
            EXEC dbo.sp_Utility_Monthly_Kvh
                @month = :month,
                @fromTime = :from,
                @currentTo = :currentTo,
                @prevFrom = :prevFrom,
                @prevTo = :prevTo,
                @exchange = :exchange,
                @sepzone = :sepzone
            """,
			nativeQuery = true
	)
	List<MonthlySummaryProjection> sumMonthlyKvhRaw(

			@Param("month")
			String month,

			@Param("from")
			LocalDateTime from,

			@Param("currentTo")
			LocalDateTime currentTo,

			@Param("prevFrom")
			LocalDateTime prevFrom,

			@Param("prevTo")
			LocalDateTime prevTo,

			@Param("exchange")
			BigDecimal exchange,

			@Param("sepzone")
			BigDecimal sepzone
	);


	@Query(value = """
    /* =========================================================
     * MONTHLY SUMMARY - KVH
     *
     * OPTIMIZED VERSION
     *
     * - Dedup F2_Utility_Para
     * - Không join F2_Utility_Scada nếu không cần FAC
     * - CURRENT + PREV đọc chung History range
     * - Solar CURRENT + PREV đọc chung History range
     * - Bỏ ISNULL(MTD, '')
     * - Giữ nguyên output MonthlySummaryProjection
     * ========================================================= */

    WITH

    /* =========================================================
     * 1. PARA DEDUP
     *
     * Chống trường hợp master khai báo trùng:
     *
     * box_device_id + plc_address + name_en + unit
     * ========================================================= */

    ParaDedup AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address,
            pa.name_en,
            pa.unit

        FROM dbo.F2_Utility_Para pa

        WHERE
               pa.name_en = 'Total Energy Consumption'
            OR pa.name_en LIKE 'Cooling tank%'
            OR pa.name_en = 'Data Pipeline pressure'
            OR pa.name_en =
               'Sensor compressed air pressure Data'
    ),


    /* =========================================================
     * 2. CHANNEL DEDUP
     *
     * Một box_device_id có thể xuất hiện nhiều lần ở channel.
     * Chỉ giữ mapping cần thiết.
     * ========================================================= */

    ChannelDedup AS (
        SELECT DISTINCT
            ch.box_device_id,

            UPPER(
                LTRIM(
                    RTRIM(
                        ISNULL(
                            ch.box_id,
                            ''
                        )
                    )
                )
            ) AS normalized_box_id

        FROM dbo.F2_Utility_Scada_Channel ch
    ),


    /* =========================================================
     * 3. NORMAL TARGET PARA
     *
     * Không lấy Solar.
     * ========================================================= */

    TargetPara AS (
        SELECT DISTINCT
            pa.name_en,

            CASE
                WHEN pa.name_en =
                     'Total Energy Consumption'
                    THEN 'Electricity'

                WHEN pa.name_en LIKE
                     'Cooling tank%'
                    THEN 'Water'

                WHEN pa.name_en =
                     'Data Pipeline pressure'
                    THEN 'Water'

                WHEN pa.name_en =
                     'Sensor compressed air pressure Data'
                    THEN 'Compressed Air'

                ELSE 'UNKNOWN'
            END AS cate,

            pa.unit,
            pa.box_device_id,
            pa.plc_address

        FROM ParaDedup pa

        INNER JOIN ChannelDedup ch
            ON ch.box_device_id =
               pa.box_device_id

        WHERE
            ch.normalized_box_id <> 'SOLAR'
    ),


    /* =========================================================
     * 4. SOLAR TARGET PARA
     *
     * KVH = lấy toàn bộ Solar.
     * ========================================================= */

    SolarTargetPara AS (
        SELECT DISTINCT
            pa.box_device_id,
            pa.plc_address

        FROM ParaDedup pa

        INNER JOIN ChannelDedup ch
            ON ch.box_device_id =
               pa.box_device_id

        WHERE
            pa.name_en =
                'Total Energy Consumption'

            AND ch.normalized_box_id =
                'SOLAR'
    ),


    /* =========================================================
     * 5. SOLAR BASE
     *
     * CURRENT + PREV chỉ đọc History trong một CTE.
     * ========================================================= */

    SolarBase AS (
        SELECT
            CASE
                WHEN hm.pick_at >= :from
                 AND hm.pick_at < :currentTo
                    THEN 'CURRENT'

                WHEN hm.pick_at >= :prevFrom
                 AND hm.pick_at < :prevTo
                    THEN 'PREV'
            END AS period_type,

            hm.pick_at,

            CAST(
                hm.[value]
                AS DECIMAL(19,6)
            ) AS solar_value,

            DATEPART(
                HOUR,
                hm.pick_at
            ) AS HourNumber,

            CASE
                WHEN DATEPART(
                    WEEKDAY,
                    CAST(
                        hm.pick_at
                        AS DATE
                    )
                ) = 1
                    THEN '1'

                ELSE '2-7'
            END AS WD

        FROM SolarTargetPara stp

        INNER JOIN
            dbo.F2_Utility_Para_History_Main hm
            ON hm.box_device_id =
               stp.box_device_id
           AND hm.plc_address =
               stp.plc_address

        WHERE
            hm.pick_at >= :prevFrom
            AND hm.pick_at < :currentTo

            AND hm.[value] > 0

            AND hm.MTD = 'MTD'

            AND (
                   (
                       hm.pick_at >= :from
                       AND hm.pick_at < :currentTo
                   )

                OR (
                       hm.pick_at >= :prevFrom
                       AND hm.pick_at < :prevTo
                   )
            )
    ),


    /* =========================================================
     * 6. SOLAR HOURLY
     * ========================================================= */

    SolarHourly AS (
        SELECT
            period_type,
            WD,
            HourNumber,

            SUM(
                solar_value
            ) AS solar_hour_value

        FROM SolarBase

        WHERE
            period_type IS NOT NULL

        GROUP BY
            period_type,
            WD,
            HourNumber
    ),


    /* =========================================================
     * 7. SOLAR MONTHLY
     * ========================================================= */

    SolarMonthly AS (
        SELECT
            CAST(
                COALESCE(
                    SUM(
                        CASE
                            WHEN period_type =
                                 'CURRENT'
                                THEN solar_value

                            ELSE 0
                        END
                    ),
                    0
                )
                AS DECIMAL(18,2)
            ) AS solarValue,

            CAST(
                COALESCE(
                    SUM(
                        CASE
                            WHEN period_type =
                                 'PREV'
                                THEN solar_value

                            ELSE 0
                        END
                    ),
                    0
                )
                AS DECIMAL(18,2)
            ) AS prevSolarValue

        FROM SolarBase
    ),


    /* =========================================================
     * 8. NORMAL UTILITY BASE
     *
     * CURRENT + PREV dùng chung một lần đọc range History.
     * ========================================================= */

    Base AS (
        SELECT
            CASE
                WHEN hi.pick_at >= :from
                 AND hi.pick_at < :currentTo
                    THEN 'CURRENT'

                WHEN hi.pick_at >= :prevFrom
                 AND hi.pick_at < :prevTo
                    THEN 'PREV'
            END AS period_type,

            tp.name_en,
            tp.cate,
            tp.unit,

            hi.pick_at,

            CAST(
                hi.[value]
                AS DECIMAL(19,6)
            ) AS [value],

            DATEPART(
                HOUR,
                hi.pick_at
            ) AS HourNumber,

            CASE
                WHEN DATEPART(
                    WEEKDAY,
                    CAST(
                        hi.pick_at
                        AS DATE
                    )
                ) = 1
                    THEN '1'

                ELSE '2-7'
            END AS WD

        FROM TargetPara tp

        INNER JOIN
            dbo.F2_Utility_Para_History_Main hi
            ON hi.box_device_id =
               tp.box_device_id
           AND hi.plc_address =
               tp.plc_address

        WHERE
            hi.pick_at >= :prevFrom
            AND hi.pick_at < :currentTo

            AND hi.[value] > 0

            /* Electricity chỉ lấy MTD */
            AND (
                tp.cate <> 'Electricity'
                OR hi.MTD = 'MTD'
            )

            AND (
                   (
                       hi.pick_at >= :from
                       AND hi.pick_at < :currentTo
                   )

                OR (
                       hi.pick_at >= :prevFrom
                       AND hi.pick_at < :prevTo
                   )
            )
    ),


    /* =========================================================
     * 9. HOURS 0 -> 23
     * ========================================================= */

    Hours AS (
        SELECT
            v.n

        FROM (
            VALUES
                (0),(1),(2),(3),(4),(5),
                (6),(7),(8),(9),(10),(11),
                (12),(13),(14),(15),(16),(17),
                (18),(19),(20),(21),(22),(23)
        ) v(n)
    ),


    /* =========================================================
     * 10. ELECTRICITY RATE BY HOUR
     * ========================================================= */

    HourCost AS (
        SELECT
            c.WD,
            h.n AS HourNumber,

            SUM(
                CASE
                    /* =========================================
                     * Normal range:
                     * from < to
                     * ========================================= */

                    WHEN c.frTime < c.toTime

                    THEN
                        (
                            CASE
                                WHEN c.toTime < h.n + 1
                                    THEN c.toTime

                                ELSE h.n + 1
                            END
                        )
                        -
                        (
                            CASE
                                WHEN c.frTime > h.n
                                    THEN c.frTime

                                ELSE h.n
                            END
                        )


                    /* =========================================
                     * Range qua midnight
                     * ========================================= */

                    ELSE
                        CASE
                            WHEN h.n >=
                                 FLOOR(
                                     c.frTime
                                 )

                            THEN
                                (
                                    CASE
                                        WHEN 24.0 <
                                             h.n + 1
                                            THEN 24.0

                                        ELSE h.n + 1
                                    END
                                )
                                -
                                (
                                    CASE
                                        WHEN c.frTime >
                                             h.n
                                            THEN c.frTime

                                        ELSE h.n
                                    END
                                )

                            ELSE
                                (
                                    CASE
                                        WHEN c.toTime <
                                             h.n + 1
                                            THEN c.toTime

                                        ELSE h.n + 1
                                    END
                                )
                                -
                                (
                                    CASE
                                        WHEN 0.0 > h.n
                                            THEN 0.0

                                        ELSE h.n
                                    END
                                )
                        END

                END
                * c.vnd
            ) AS weighted_vnd_sum,


            SUM(
                CASE
                    WHEN c.frTime < c.toTime

                    THEN
                        (
                            CASE
                                WHEN c.toTime <
                                     h.n + 1
                                    THEN c.toTime

                                ELSE h.n + 1
                            END
                        )
                        -
                        (
                            CASE
                                WHEN c.frTime >
                                     h.n
                                    THEN c.frTime

                                ELSE h.n
                            END
                        )

                    ELSE
                        CASE
                            WHEN h.n >=
                                 FLOOR(
                                     c.frTime
                                 )

                            THEN
                                (
                                    CASE
                                        WHEN 24.0 <
                                             h.n + 1
                                            THEN 24.0

                                        ELSE h.n + 1
                                    END
                                )
                                -
                                (
                                    CASE
                                        WHEN c.frTime >
                                             h.n
                                            THEN c.frTime

                                        ELSE h.n
                                    END
                                )

                            ELSE
                                (
                                    CASE
                                        WHEN c.toTime <
                                             h.n + 1
                                            THEN c.toTime

                                        ELSE h.n + 1
                                    END
                                )
                                -
                                (
                                    CASE
                                        WHEN 0.0 > h.n
                                            THEN 0.0

                                        ELSE h.n
                                    END
                                )
                        END
                END
            ) AS total_hours

        FROM dbo.F2_Utility_Cost_Master c

        CROSS JOIN Hours h

        WHERE
            (
                c.frTime < c.toTime

                AND h.n < c.toTime

                AND h.n + 1 >
                    c.frTime
            )

            OR

            (
                c.frTime > c.toTime

                AND (
                       h.n + 1 >
                           c.frTime

                    OR h.n <
                       c.toTime
                )
            )

        GROUP BY
            c.WD,
            h.n
    ),


    /* =========================================================
     * 11. FINAL RATE
     * ========================================================= */

    FinalRate AS (
        SELECT
            WD,
            HourNumber,

            weighted_vnd_sum
            /
            NULLIF(
                total_hours,
                0
            ) AS vnd_rate

        FROM HourCost
    ),


    /* =========================================================
     * 12. ELECTRICITY HOURLY
     * ========================================================= */

    EnergyHourly AS (
        SELECT
            period_type,
            name_en,
            cate,
            unit,
            WD,
            HourNumber,

            SUM(
                [value]
            ) AS hour_value

        FROM Base

        WHERE
            period_type IS NOT NULL

            AND name_en =
                'Total Energy Consumption'

        GROUP BY
            period_type,
            name_en,
            cate,
            unit,
            WD,
            HourNumber
    ),


    /* =========================================================
     * 13. ELECTRICITY MONTHLY GROSS
     * ========================================================= */

    EnergyMonthly AS (
        SELECT
            e.name_en AS name,
            e.cate,
            e.unit,

            /* ===============================================
             * Gross energy current
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'CURRENT'
                        THEN e.hour_value

                    ELSE 0
                END
            ) AS gross_value,


            /* ===============================================
             * Gross energy previous
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'PREV'
                        THEN e.hour_value

                    ELSE 0
                END
            ) AS gross_prev_value,


            /* ===============================================
             * Gross VND current
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'CURRENT'

                    THEN
                        e.hour_value
                        *
                        r.vnd_rate

                    ELSE 0
                END
            ) AS gross_vndCost,


            /* ===============================================
             * Gross VND previous
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'PREV'

                    THEN
                        e.hour_value
                        *
                        r.vnd_rate

                    ELSE 0
                END
            ) AS gross_prevVndCost,


            /* ===============================================
             * Solar cost current
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'CURRENT'

                    THEN
                        COALESCE(
                            sh.solar_hour_value,
                            0
                        )
                        *
                        r.vnd_rate

                    ELSE 0
                END
            ) AS solar_vndCost,


            /* ===============================================
             * Solar cost previous
             * =============================================== */

            SUM(
                CASE
                    WHEN e.period_type =
                         'PREV'

                    THEN
                        COALESCE(
                            sh.solar_hour_value,
                            0
                        )
                        *
                        r.vnd_rate

                    ELSE 0
                END
            ) AS prev_solar_vndCost

        FROM EnergyHourly e

        INNER JOIN FinalRate r
            ON r.WD =
               e.WD

           AND r.HourNumber =
               e.HourNumber

        LEFT JOIN SolarHourly sh
            ON sh.period_type =
               e.period_type

           AND sh.WD =
               e.WD

           AND sh.HourNumber =
               e.HourNumber

        GROUP BY
            e.name_en,
            e.cate,
            e.unit
    ),


    /* =========================================================
     * 14. GRID / NET ELECTRICITY
     *
     * GRID = GROSS - SOLAR
     * ========================================================= */

    EnergyNetMonthly AS (
        SELECT
            e.name,
            e.cate,
            e.unit,

            /* GRID CURRENT */

            CAST(
                CASE
                    WHEN COALESCE(
                        e.gross_value,
                        0
                    )
                    >
                    COALESCE(
                        s.solarValue,
                        0
                    )

                    THEN
                        COALESCE(
                            e.gross_value,
                            0
                        )
                        -
                        COALESCE(
                            s.solarValue,
                            0
                        )

                    ELSE 0
                END
                AS DECIMAL(18,2)
            ) AS value,


            /* GRID PREVIOUS */

            CAST(
                CASE
                    WHEN COALESCE(
                        e.gross_prev_value,
                        0
                    )
                    >
                    COALESCE(
                        s.prevSolarValue,
                        0
                    )

                    THEN
                        COALESCE(
                            e.gross_prev_value,
                            0
                        )
                        -
                        COALESCE(
                            s.prevSolarValue,
                            0
                        )

                    ELSE 0
                END
                AS DECIMAL(18,2)
            ) AS prevValue,


            /* GROSS CURRENT */

            CAST(
                COALESCE(
                    e.gross_value,
                    0
                )
                AS DECIMAL(18,2)
            ) AS totalEnergyValue,


            /* GROSS PREVIOUS */

            CAST(
                COALESCE(
                    e.gross_prev_value,
                    0
                )
                AS DECIMAL(18,2)
            ) AS prevTotalEnergyValue,


            /* SOLAR CURRENT */

            CAST(
                COALESCE(
                    s.solarValue,
                    0
                )
                AS DECIMAL(18,2)
            ) AS solarValue,


            /* SOLAR PREVIOUS */

            CAST(
                COALESCE(
                    s.prevSolarValue,
                    0
                )
                AS DECIMAL(18,2)
            ) AS prevSolarValue,


            /* VND CURRENT */

            CAST(
                CASE
                    WHEN COALESCE(
                        e.gross_vndCost,
                        0
                    )
                    >
                    COALESCE(
                        e.solar_vndCost,
                        0
                    )

                    THEN
                        COALESCE(
                            e.gross_vndCost,
                            0
                        )
                        -
                        COALESCE(
                            e.solar_vndCost,
                            0
                        )

                    ELSE 0
                END
                AS DECIMAL(18,2)
            ) AS vndCost,


            /* VND PREVIOUS */

            CAST(
                CASE
                    WHEN COALESCE(
                        e.gross_prevVndCost,
                        0
                    )
                    >
                    COALESCE(
                        e.prev_solar_vndCost,
                        0
                    )

                    THEN
                        COALESCE(
                            e.gross_prevVndCost,
                            0
                        )
                        -
                        COALESCE(
                            e.prev_solar_vndCost,
                            0
                        )

                    ELSE 0
                END
                AS DECIMAL(18,2)
            ) AS prevVndCost,


            /* USD CURRENT */

            CAST(
                (
                    CASE
                        WHEN COALESCE(
                            e.gross_vndCost,
                            0
                        )
                        >
                        COALESCE(
                            e.solar_vndCost,
                            0
                        )

                        THEN
                            COALESCE(
                                e.gross_vndCost,
                                0
                            )
                            -
                            COALESCE(
                                e.solar_vndCost,
                                0
                            )

                        ELSE 0
                    END
                )
                /
                NULLIF(
                    :exchange,
                    0
                )
                *
                :sepzone
                AS DECIMAL(18,2)
            ) AS usdCost,


            /* USD PREVIOUS */

            CAST(
                (
                    CASE
                        WHEN COALESCE(
                            e.gross_prevVndCost,
                            0
                        )
                        >
                        COALESCE(
                            e.prev_solar_vndCost,
                            0
                        )

                        THEN
                            COALESCE(
                                e.gross_prevVndCost,
                                0
                            )
                            -
                            COALESCE(
                                e.prev_solar_vndCost,
                                0
                            )

                        ELSE 0
                    END
                )
                /
                NULLIF(
                    :exchange,
                    0
                )
                *
                :sepzone
                AS DECIMAL(18,2)
            ) AS prevUsdCost

        FROM EnergyMonthly e

        CROSS JOIN SolarMonthly s
    ),


    /* =========================================================
     * 15. WATER TEMPERATURE
     * ========================================================= */

    WaterMonthly AS (
        SELECT
            'Cooling Tank Temperature'
                AS name,

            'Water'
                AS cate,

            MAX(
                unit
            ) AS unit,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS avgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS minValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS maxValue,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevAvgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMinValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMaxValue

        FROM Base

        WHERE
            period_type IS NOT NULL

            AND name_en LIKE
                'Cooling tank%'
    ),


    /* =========================================================
     * 16. WATER PIPELINE PRESSURE
     * ========================================================= */

    PipelinePressureMonthly AS (
        SELECT
            'Data Pipeline pressure'
                AS name,

            'Water'
                AS cate,

            MAX(
                unit
            ) AS unit,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS avgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS minValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS maxValue,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevAvgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMinValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMaxValue

        FROM Base

        WHERE
            period_type IS NOT NULL

            AND name_en =
                'Data Pipeline pressure'
    ),


    /* =========================================================
     * 17. COMPRESSED AIR
     * ========================================================= */

    AirMonthly AS (
        SELECT
            'Sensor compressed air pressure Data'
                AS name,

            'Compressed Air'
                AS cate,

            MAX(
                unit
            ) AS unit,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS avgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS minValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'CURRENT'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS maxValue,


            CAST(
                ROUND(
                    AVG(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevAvgValue,


            CAST(
                ROUND(
                    MIN(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMinValue,


            CAST(
                ROUND(
                    MAX(
                        CASE
                            WHEN period_type =
                                 'PREV'

                            THEN CAST(
                                [value]
                                AS DECIMAL(18,4)
                            )
                        END
                    ),
                    1
                )
                AS DECIMAL(18,1)
            ) AS prevMaxValue

        FROM Base

        WHERE
            period_type IS NOT NULL

            AND name_en =
                'Sensor compressed air pressure Data'
    ),


    /* =========================================================
     * 18. LAST PICK
     * ========================================================= */

    LastPick AS (
        SELECT
            MAX(
                pick_at
            ) AS pickAt

        FROM Base

        WHERE
            period_type =
                'CURRENT'
    ),


    /* =========================================================
     * 19. FINAL ROWS
     * ========================================================= */

    FinalRows AS (

        /* Electricity */

        SELECT
            e.name,
            e.cate,
            e.unit,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS minValue,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS maxValue,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS prevMinValue,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS prevMaxValue,

            CAST(
                e.value
                AS DECIMAL(18,2)
            ) AS value,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS avgValue,

            CAST(
                e.vndCost
                AS DECIMAL(18,2)
            ) AS vndCost,

            CAST(
                e.usdCost
                AS DECIMAL(18,2)
            ) AS usdCost,

            CAST(
                e.prevValue
                AS DECIMAL(18,2)
            ) AS prevValue,

            CAST(
                NULL AS DECIMAL(18,1)
            ) AS prevAvgValue,

            CAST(
                e.prevVndCost
                AS DECIMAL(18,2)
            ) AS prevVndCost,

            CAST(
                e.prevUsdCost
                AS DECIMAL(18,2)
            ) AS prevUsdCost

        FROM EnergyNetMonthly e


        UNION ALL


        /* Cooling Tank */

        SELECT
            w.name,
            w.cate,
            w.unit,

            w.minValue,
            w.maxValue,
            w.prevMinValue,
            w.prevMaxValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            w.avgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            w.prevAvgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            )

        FROM WaterMonthly w


        UNION ALL


        /* Pipeline Pressure */

        SELECT
            p.name,
            p.cate,
            p.unit,

            p.minValue,
            p.maxValue,
            p.prevMinValue,
            p.prevMaxValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            p.avgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            p.prevAvgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            )

        FROM PipelinePressureMonthly p


        UNION ALL


        /* Air */

        SELECT
            a.name,
            a.cate,
            a.unit,

            a.minValue,
            a.maxValue,
            a.prevMinValue,
            a.prevMaxValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            a.avgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            a.prevAvgValue,

            CAST(
                NULL AS DECIMAL(18,2)
            ),

            CAST(
                NULL AS DECIMAL(18,2)
            )

        FROM AirMonthly a
    )


    /* =========================================================
     * 20. FINAL SELECT
     * ========================================================= */

    SELECT
        f.name AS name,

        f.cate AS cate,

        f.unit AS unit,

        :month AS month,

        f.minValue AS minValue,

        f.maxValue AS maxValue,

        f.prevMinValue AS prevMinValue,

        f.prevMaxValue AS prevMaxValue,

        f.value AS value,

        f.avgValue AS avgValue,

        f.vndCost AS vndCost,

        f.usdCost AS usdCost,

        f.prevValue AS prevValue,

        f.prevAvgValue AS prevAvgValue,

        f.prevVndCost AS prevVndCost,

        f.prevUsdCost AS prevUsdCost,


        /* =====================================================
         * DELTA VALUE
         * ===================================================== */

        CAST(
            COALESCE(
                f.value,
                f.avgValue,
                0
            )
            -
            COALESCE(
                f.prevValue,
                f.prevAvgValue,
                0
            )
            AS DECIMAL(18,2)
        ) AS deltaValue,


        /* =====================================================
         * DELTA %
         * ===================================================== */

        CAST(
            CASE
                WHEN COALESCE(
                    f.prevValue,
                    f.prevAvgValue,
                    0
                ) = 0

                    THEN NULL

                ELSE
                    (
                        (
                            COALESCE(
                                f.value,
                                f.avgValue,
                                0
                            )
                            -
                            COALESCE(
                                f.prevValue,
                                f.prevAvgValue,
                                0
                            )
                        )
                        /
                        NULLIF(
                            COALESCE(
                                f.prevValue,
                                f.prevAvgValue,
                                0
                            ),
                            0
                        )
                    )
                    * 100
            END
            AS DECIMAL(10,2)
        ) AS deltaPercent,


        lp.pickAt AS pickAt,


        /* =====================================================
         * SOLAR CURRENT
         * ===================================================== */

        CASE
            WHEN f.cate =
                 'Electricity'

                THEN sm.solarValue
        END AS solarValue,


        /* =====================================================
         * SOLAR PREVIOUS
         * ===================================================== */

        CASE
            WHEN f.cate =
                 'Electricity'

                THEN sm.prevSolarValue
        END AS prevSolarValue,


        /* =====================================================
         * TOTAL ENERGY CURRENT
         *
         * Grid + Solar = Gross
         * ===================================================== */

        CASE
            WHEN f.cate =
                 'Electricity'

            THEN
                CAST(
                    COALESCE(
                        f.value,
                        0
                    )
                    +
                    COALESCE(
                        sm.solarValue,
                        0
                    )
                    AS DECIMAL(18,2)
                )
        END AS totalEnergyValue,


        /* =====================================================
         * TOTAL ENERGY PREVIOUS
         * ===================================================== */

        CASE
            WHEN f.cate =
                 'Electricity'

            THEN
                CAST(
                    COALESCE(
                        f.prevValue,
                        0
                    )
                    +
                    COALESCE(
                        sm.prevSolarValue,
                        0
                    )
                    AS DECIMAL(18,2)
                )
        END AS prevTotalEnergyValue,


        /* =====================================================
         * SOLAR SHARE %
         * ===================================================== */

        CASE
            WHEN f.cate =
                 'Electricity'

             AND (
                    COALESCE(
                        f.value,
                        0
                    )
                    +
                    COALESCE(
                        sm.solarValue,
                        0
                    )
                 ) > 0

            THEN
                CAST(
                    COALESCE(
                        sm.solarValue,
                        0
                    )
                    /
                    NULLIF(
                        COALESCE(
                            f.value,
                            0
                        )
                        +
                        COALESCE(
                            sm.solarValue,
                            0
                        ),
                        0
                    )
                    * 100

                    AS DECIMAL(10,2)
                )
        END AS solarSharePercent

    FROM FinalRows f

    CROSS JOIN LastPick lp

    CROSS JOIN SolarMonthly sm

    ORDER BY
        CASE
            WHEN f.cate =
                 'Electricity'
                THEN 1

            WHEN f.cate =
                 'Water'

             AND f.name =
                 'Cooling Tank Temperature'
                THEN 2

            WHEN f.cate =
                 'Water'

             AND f.name =
                 'Data Pipeline pressure'
                THEN 3

            WHEN f.cate =
                 'Compressed Air'
                THEN 4

            ELSE 9
        END

    OPTION (RECOMPILE)
    """, nativeQuery = true)
	List<MonthlySummaryProjection> sumMonthlyKvhRaw1(

			@Param("month")
			String month,

			@Param("from")
			LocalDateTime from,

			@Param("currentTo")
			LocalDateTime currentTo,

			@Param("prevFrom")
			LocalDateTime prevFrom,

			@Param("prevTo")
			LocalDateTime prevTo,

			@Param("exchange")
			BigDecimal exchange,

			@Param("sepzone")
			BigDecimal sepzone
	);
}