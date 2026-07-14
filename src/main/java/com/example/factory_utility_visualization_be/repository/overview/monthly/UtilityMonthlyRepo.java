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
	// - Lọc sensor trước.
	// - Sau đó mới join history.
	// - Fac_A vẫn lấy thêm Compressed Air từ Fac_B theo rule cũ.
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
			            WHEN pa.name_en = 'Sensor compressed air pressure Data'
			                THEN 'Compressed Air'
			            ELSE ch.cate
			        END AS cate,
			        pa.unit,
			        pa.box_device_id,
			        pa.plc_address
			    FROM dbo.F2_Utility_Para pa
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON pa.box_device_id = ch.box_device_id
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id
			    WHERE (
			        (
			            sc.fac = :fac
			            AND (
			                   pa.name_en = 'Total Energy Consumption'
			                OR pa.name_en LIKE '%Cooling tank%'
			                OR pa.name_en = 'Sensor compressed air pressure Data'
			            )
			        )
			        OR
			        (
			            :fac = 'Fac_A'
			            AND sc.fac = 'Fac_B'
			            AND pa.name_en = 'Sensor compressed air pressure Data'
			        )
			    )
			),
			
			Base AS (
			    SELECT
			        'CURRENT' AS period_type,
			        tp.fac,
			        tp.name_en,
			        tp.cate,
			        tp.unit,
			        hi.pick_at,
			        hi.[value],
			        DATEPART(HOUR, hi.pick_at) AS HourNumber,
			        CASE
			            WHEN DATEPART(WEEKDAY, CAST(hi.pick_at AS DATE)) = 1 THEN '1'
			            ELSE '2-7'
			        END AS WD
			    FROM TargetPara tp
			    INNER JOIN dbo.F2_Utility_Para_History_Main hi
			        ON hi.box_device_id = tp.box_device_id
			       AND hi.plc_address = tp.plc_address
			       AND hi.pick_at >= :from
			       AND hi.pick_at <  :currentTo
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
			        DATEPART(HOUR, hi.pick_at) AS HourNumber,
			        CASE
			            WHEN DATEPART(WEEKDAY, CAST(hi.pick_at AS DATE)) = 1 THEN '1'
			            ELSE '2-7'
			        END AS WD
			    FROM TargetPara tp
			    INNER JOIN dbo.F2_Utility_Para_History_Main hi
			        ON hi.box_device_id = tp.box_device_id
			       AND hi.plc_address = tp.plc_address
			       AND hi.pick_at >= :prevFrom
			       AND hi.pick_at <  :prevTo
			       AND hi.[value] > 0
			),
			
			Hours AS (
			    SELECT v.n
			    FROM (VALUES
			        (0),(1),(2),(3),(4),(5),(6),(7),
			        (8),(9),(10),(11),(12),(13),(14),(15),
			        (16),(17),(18),(19),(20),(21),(22),(23)
			    ) v(n)
			),
			
			HourCost AS (
			    SELECT
			        c.WD,
			        h.n AS HourNumber,
			
			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime THEN
			                    (
			                        CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                    )
			                    -
			                    (
			                        CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                    )
			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime) THEN
			                            (
			                                CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                            )
			                        ELSE
			                            (
			                                CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END
			                            )
			                    END
			            END * c.vnd
			        ) AS weighted_vnd_sum,
			
			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime THEN
			                    (
			                        CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                    )
			                    -
			                    (
			                        CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                    )
			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime) THEN
			                            (
			                                CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                            )
			                        ELSE
			                            (
			                                CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END
			                            )
			                    END
			            END
			        ) AS total_hours
			
			    FROM dbo.F2_Utility_Cost_Master c
			    CROSS JOIN Hours h
			    WHERE (
			        (c.frTime < c.toTime AND h.n < c.toTime AND h.n + 1 > c.frTime)
			        OR
			        (c.frTime > c.toTime AND (h.n + 1 > c.frTime OR h.n < c.toTime))
			    )
			    GROUP BY c.WD, h.n
			),
			
			FinalRate AS (
			    SELECT
			        WD,
			        HourNumber,
			        weighted_vnd_sum / NULLIF(total_hours, 0) AS vnd_rate
			    FROM HourCost
			),
			
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
			    WHERE name_en = 'Total Energy Consumption'
			    GROUP BY
			        period_type,
			        name_en,
			        cate,
			        unit,
			        WD,
			        HourNumber
			),
			
			EnergyMonthly AS (
			    SELECT
			        e.name_en AS name,
			        e.cate,
			        e.unit,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value END) AS value,
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value END) AS prevValue,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value * r.vnd_rate END) AS vndCost,
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value * r.vnd_rate END) AS prevVndCost,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value * r.vnd_rate END)
			            / NULLIF(:exchange, 0) * :sepzone AS usdCost,
			
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value * r.vnd_rate END)
			            / NULLIF(:exchange, 0) * :sepzone AS prevUsdCost
			
			    FROM EnergyHourly e
			    INNER JOIN FinalRate r
			        ON e.WD = r.WD
			       AND e.HourNumber = r.HourNumber
			    GROUP BY
			        e.name_en,
			        e.cate,
			        e.unit
			),
			
			WaterMonthly AS (
			    SELECT
			        'Cooling Tank Temperature' AS name,
			        'Water' AS cate,
			        MAX(unit) AS unit,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMaxValue
			
			    FROM Base
			    WHERE name_en LIKE '%Cooling tank%'
			),
			
			AirMonthly AS (
			    SELECT
			        'Sensor compressed air pressure Data' AS name,
			        'Compressed Air' AS cate,
			        MAX(unit) AS unit,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMaxValue
			
			    FROM Base
			    WHERE name_en = 'Sensor compressed air pressure Data'
			),
			
			LastPick AS (
			    SELECT MAX(pick_at) AS pickAt
			    FROM Base
			    WHERE period_type = 'CURRENT'
			),
			
			FinalRows AS (
			    SELECT
			        e.name,
			        e.cate,
			        e.unit,
			
			        CAST(NULL AS DECIMAL(18,1)) AS minValue,
			        CAST(NULL AS DECIMAL(18,1)) AS maxValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevMinValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevMaxValue,
			
			        CAST(e.value AS DECIMAL(18,2)) AS value,
			        CAST(NULL AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(e.vndCost AS DECIMAL(18,2)) AS vndCost,
			        CAST(e.usdCost AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(e.prevValue AS DECIMAL(18,2)) AS prevValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(e.prevVndCost AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(e.prevUsdCost AS DECIMAL(18,2)) AS prevUsdCost
			    FROM EnergyMonthly e
			
			    UNION ALL
			
			    SELECT
			        w.name,
			        w.cate,
			        w.unit,
			
			        w.minValue,
			        w.maxValue,
			        w.prevMinValue,
			        w.prevMaxValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS value,
			        w.avgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS vndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevValue,
			        w.prevAvgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS prevUsdCost
			    FROM WaterMonthly w
			
			    UNION ALL
			
			    SELECT
			        a.name,
			        a.cate,
			        a.unit,
			
			        a.minValue,
			        a.maxValue,
			        a.prevMinValue,
			        a.prevMaxValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS value,
			        a.avgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS vndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevValue,
			        a.prevAvgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS prevUsdCost
			    FROM AirMonthly a
			)
			
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
			        COALESCE(f.value, f.avgValue, 0)
			        -
			        COALESCE(f.prevValue, f.prevAvgValue, 0)
			        AS DECIMAL(18,2)
			    ) AS deltaValue,
			
			    CAST(
			        CASE
			            WHEN COALESCE(f.prevValue, f.prevAvgValue, 0) = 0 THEN NULL
			            ELSE (
			                (
			                    COALESCE(f.value, f.avgValue, 0)
			                    -
			                    COALESCE(f.prevValue, f.prevAvgValue, 0)
			                )
			                / COALESCE(f.prevValue, f.prevAvgValue, 0)
			            ) * 100
			        END AS DECIMAL(10,2)
			    ) AS deltaPercent,
			
			    lp.pickAt AS pickAt
			
			FROM FinalRows f
			CROSS JOIN LastPick lp
			ORDER BY
			    CASE
			        WHEN f.cate = 'Electricity' THEN 1
			        WHEN f.cate = 'Water' THEN 2
			        WHEN f.cate = 'Compressed Air' THEN 3
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

	// =========================================================
	// KVH
	// - Không dùng điều kiện :fac = 'KVH' OR ...
	// - Query riêng để SQL Server chọn plan nhẹ hơn.
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
			            WHEN pa.name_en = 'Sensor compressed air pressure Data'
			                THEN 'Compressed Air'
			            ELSE ch.cate
			        END AS cate,
			        pa.unit,
			        pa.box_device_id,
			        pa.plc_address
			    FROM dbo.F2_Utility_Para pa
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON pa.box_device_id = ch.box_device_id
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id
			    WHERE (
			           pa.name_en = 'Total Energy Consumption'
			        OR pa.name_en LIKE '%Cooling tank%'
			        OR pa.name_en = 'Sensor compressed air pressure Data'
			    )
			),
			
			Base AS (
			    SELECT
			        'CURRENT' AS period_type,
			        tp.fac,
			        tp.name_en,
			        tp.cate,
			        tp.unit,
			        hi.pick_at,
			        hi.[value],
			        DATEPART(HOUR, hi.pick_at) AS HourNumber,
			        CASE
			            WHEN DATEPART(WEEKDAY, CAST(hi.pick_at AS DATE)) = 1 THEN '1'
			            ELSE '2-7'
			        END AS WD
			    FROM TargetPara tp
			    INNER JOIN dbo.F2_Utility_Para_History_Main hi
			        ON hi.box_device_id = tp.box_device_id
			       AND hi.plc_address = tp.plc_address
			       AND hi.pick_at >= :from
			       AND hi.pick_at <  :currentTo
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
			        DATEPART(HOUR, hi.pick_at) AS HourNumber,
			        CASE
			            WHEN DATEPART(WEEKDAY, CAST(hi.pick_at AS DATE)) = 1 THEN '1'
			            ELSE '2-7'
			        END AS WD
			    FROM TargetPara tp
			    INNER JOIN dbo.F2_Utility_Para_History_Main hi
			        ON hi.box_device_id = tp.box_device_id
			       AND hi.plc_address = tp.plc_address
			       AND hi.pick_at >= :prevFrom
			       AND hi.pick_at <  :prevTo
			       AND hi.[value] > 0
			),
			
			Hours AS (
			    SELECT v.n
			    FROM (VALUES
			        (0),(1),(2),(3),(4),(5),(6),(7),
			        (8),(9),(10),(11),(12),(13),(14),(15),
			        (16),(17),(18),(19),(20),(21),(22),(23)
			    ) v(n)
			),
			
			HourCost AS (
			    SELECT
			        c.WD,
			        h.n AS HourNumber,
			
			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime THEN
			                    (
			                        CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                    )
			                    -
			                    (
			                        CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                    )
			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime) THEN
			                            (
			                                CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                            )
			                        ELSE
			                            (
			                                CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END
			                            )
			                    END
			            END * c.vnd
			        ) AS weighted_vnd_sum,
			
			        SUM(
			            CASE
			                WHEN c.frTime < c.toTime THEN
			                    (
			                        CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                    )
			                    -
			                    (
			                        CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                    )
			                ELSE
			                    CASE
			                        WHEN h.n >= FLOOR(c.frTime) THEN
			                            (
			                                CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END
			                            )
			                        ELSE
			                            (
			                                CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END
			                            )
			                            -
			                            (
			                                CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END
			                            )
			                    END
			            END
			        ) AS total_hours
			
			    FROM dbo.F2_Utility_Cost_Master c
			    CROSS JOIN Hours h
			    WHERE (
			        (c.frTime < c.toTime AND h.n < c.toTime AND h.n + 1 > c.frTime)
			        OR
			        (c.frTime > c.toTime AND (h.n + 1 > c.frTime OR h.n < c.toTime))
			    )
			    GROUP BY c.WD, h.n
			),
			
			FinalRate AS (
			    SELECT
			        WD,
			        HourNumber,
			        weighted_vnd_sum / NULLIF(total_hours, 0) AS vnd_rate
			    FROM HourCost
			),
			
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
			    WHERE name_en = 'Total Energy Consumption'
			    GROUP BY
			        period_type,
			        name_en,
			        cate,
			        unit,
			        WD,
			        HourNumber
			),
			
			EnergyMonthly AS (
			    SELECT
			        e.name_en AS name,
			        e.cate,
			        e.unit,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value END) AS value,
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value END) AS prevValue,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value * r.vnd_rate END) AS vndCost,
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value * r.vnd_rate END) AS prevVndCost,
			
			        SUM(CASE WHEN e.period_type = 'CURRENT' THEN e.hour_value * r.vnd_rate END)
			            / NULLIF(:exchange, 0) * :sepzone AS usdCost,
			
			        SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value * r.vnd_rate END)
			            / NULLIF(:exchange, 0) * :sepzone AS prevUsdCost
			
			    FROM EnergyHourly e
			    INNER JOIN FinalRate r
			        ON e.WD = r.WD
			       AND e.HourNumber = r.HourNumber
			    GROUP BY
			        e.name_en,
			        e.cate,
			        e.unit
			),
			
			WaterMonthly AS (
			    SELECT
			        'Cooling Tank Temperature' AS name,
			        'Water' AS cate,
			        MAX(unit) AS unit,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMaxValue
			
			    FROM Base
			    WHERE name_en LIKE '%Cooling tank%'
			),
			
			AirMonthly AS (
			    SELECT
			        'Sensor compressed air pressure Data' AS name,
			        'Compressed Air' AS cate,
			        MAX(unit) AS unit,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(18,4)) END), 1) AS DECIMAL(18,1)) AS prevMaxValue
			
			    FROM Base
			    WHERE name_en = 'Sensor compressed air pressure Data'
			),
			
			LastPick AS (
			    SELECT MAX(pick_at) AS pickAt
			    FROM Base
			    WHERE period_type = 'CURRENT'
			),
			
			FinalRows AS (
			    SELECT
			        e.name,
			        e.cate,
			        e.unit,
			
			        CAST(NULL AS DECIMAL(18,1)) AS minValue,
			        CAST(NULL AS DECIMAL(18,1)) AS maxValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevMinValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevMaxValue,
			
			        CAST(e.value AS DECIMAL(18,2)) AS value,
			        CAST(NULL AS DECIMAL(18,1)) AS avgValue,
			
			        CAST(e.vndCost AS DECIMAL(18,2)) AS vndCost,
			        CAST(e.usdCost AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(e.prevValue AS DECIMAL(18,2)) AS prevValue,
			        CAST(NULL AS DECIMAL(18,1)) AS prevAvgValue,
			
			        CAST(e.prevVndCost AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(e.prevUsdCost AS DECIMAL(18,2)) AS prevUsdCost
			    FROM EnergyMonthly e
			
			    UNION ALL
			
			    SELECT
			        w.name,
			        w.cate,
			        w.unit,
			
			        w.minValue,
			        w.maxValue,
			        w.prevMinValue,
			        w.prevMaxValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS value,
			        w.avgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS vndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevValue,
			        w.prevAvgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS prevUsdCost
			    FROM WaterMonthly w
			
			    UNION ALL
			
			    SELECT
			        a.name,
			        a.cate,
			        a.unit,
			
			        a.minValue,
			        a.maxValue,
			        a.prevMinValue,
			        a.prevMaxValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS value,
			        a.avgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS vndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS usdCost,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevValue,
			        a.prevAvgValue,
			
			        CAST(NULL AS DECIMAL(18,2)) AS prevVndCost,
			        CAST(NULL AS DECIMAL(18,2)) AS prevUsdCost
			    FROM AirMonthly a
			)
			
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
			        COALESCE(f.value, f.avgValue, 0)
			        -
			        COALESCE(f.prevValue, f.prevAvgValue, 0)
			        AS DECIMAL(18,2)
			    ) AS deltaValue,
			
			    CAST(
			        CASE
			            WHEN COALESCE(f.prevValue, f.prevAvgValue, 0) = 0 THEN NULL
			            ELSE (
			                (
			                    COALESCE(f.value, f.avgValue, 0)
			                    -
			                    COALESCE(f.prevValue, f.prevAvgValue, 0)
			                )
			                / COALESCE(f.prevValue, f.prevAvgValue, 0)
			            ) * 100
			        END AS DECIMAL(10,2)
			    ) AS deltaPercent,
			
			    lp.pickAt AS pickAt
			
			FROM FinalRows f
			CROSS JOIN LastPick lp
			ORDER BY
			    CASE
			        WHEN f.cate = 'Electricity' THEN 1
			        WHEN f.cate = 'Water' THEN 2
			        WHEN f.cate = 'Compressed Air' THEN 3
			        ELSE 9
			    END
			OPTION (RECOMPILE)
			""", nativeQuery = true)
	List<MonthlySummaryProjection> sumMonthlyKvhRaw(
			@Param("month") String month,
			@Param("from") LocalDateTime from,
			@Param("currentTo") LocalDateTime currentTo,
			@Param("prevFrom") LocalDateTime prevFrom,
			@Param("prevTo") LocalDateTime prevTo,
			@Param("exchange") BigDecimal exchange,
			@Param("sepzone") BigDecimal sepzone
	);
}