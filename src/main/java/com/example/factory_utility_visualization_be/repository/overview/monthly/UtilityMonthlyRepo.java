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

	@Query(value = """
            WITH CleanData AS (
                SELECT *
                FROM dbo.F2_Utility_Para_History_Main
                WHERE [value] > 0
                  AND pick_at >= :prevFrom
                  AND pick_at <  :currentTo
            ),
            Base AS (
                SELECT
                    CASE
                        WHEN hi.pick_at >= :from
                         AND hi.pick_at <  :currentTo
                            THEN 'CURRENT'

                        WHEN hi.pick_at >= :prevFrom
                         AND hi.pick_at <  :prevTo
                            THEN 'PREV'
                    END AS period_type,

                    sc.fac,
                    pa.name_en,
                    ch.cate,
                    pa.unit,
                    hi.pick_at,
                    hi.[value],

                    DATEPART(HOUR, hi.pick_at) AS HourNumber,

                    CASE
                        WHEN DATEPART(WEEKDAY, CAST(hi.pick_at AS DATE)) = 1 THEN '1'
                        ELSE '2-7'
                    END AS WD

                FROM CleanData hi

                INNER JOIN dbo.F2_Utility_Para pa
                    ON hi.box_device_id = pa.box_device_id
                   AND hi.plc_address  = pa.plc_address

                INNER JOIN dbo.F2_Utility_Scada_Channel ch
                    ON hi.box_device_id = ch.box_device_id

                INNER JOIN dbo.F2_Utility_Scada sc
                    ON ch.scada_id = sc.scada_id

                WHERE (
                       :fac = 'KVH'
                    OR (
                        :fac = 'Fac_A'
                        AND pa.name_en = 'Sensor compressed air pressure Data'
                        AND sc.fac = 'Fac_B'
                    )
                    OR sc.fac = :fac
                )
                  AND (
                         pa.name_en = 'Total Energy Consumption'
                      OR pa.name_en LIKE '%Cooling tank%'
                      OR pa.name_en = 'Sensor compressed air pressure Data'
                  )
                  AND (
                         (hi.pick_at >= :from AND hi.pick_at < :currentTo)
                      OR (hi.pick_at >= :prevFrom AND hi.pick_at < :prevTo)
                  )
            ),
            Hours AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
                UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
                UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
                UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19
                UNION ALL SELECT 20 UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23
            ),
            HourCost AS (
                SELECT
                    c.WD,
                    h.n AS HourNumber,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime THEN
                                (CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END)
                              - (CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END)
                            ELSE
                                CASE
                                    WHEN h.n >= FLOOR(c.frTime) THEN
                                        (CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END)
                                      - (CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END)
                                    ELSE
                                        (CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END)
                                      - (CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END)
                                END
                        END * c.vnd
                    ) AS weighted_vnd_sum,

                    SUM(
                        CASE
                            WHEN c.frTime < c.toTime THEN
                                (CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END)
                              - (CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END)
                            ELSE
                                CASE
                                    WHEN h.n >= FLOOR(c.frTime) THEN
                                        (CASE WHEN 24.0 < h.n + 1 THEN 24.0 ELSE h.n + 1 END)
                                      - (CASE WHEN c.frTime > h.n THEN c.frTime ELSE h.n END)
                                    ELSE
                                        (CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END)
                                      - (CASE WHEN 0.0 > h.n THEN 0.0 ELSE h.n END)
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
                  AND period_type IS NOT NULL
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
                        / :exchange * :sepzone AS usdCost,

                    SUM(CASE WHEN e.period_type = 'PREV' THEN e.hour_value * r.vnd_rate END)
                        / :exchange * :sepzone AS prevUsdCost

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
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevMaxValue
			
			    FROM Base
			    WHERE name_en LIKE '%Cooling tank%'
			),
			AirMonthly AS (
			    SELECT
			        'Sensor compressed air pressure Data' AS name,
			        'Compressed Air' AS cate,
			        MAX(unit) AS unit,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS avgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS minValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'CURRENT'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS maxValue,
			
			        CAST(ROUND(AVG(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevAvgValue,
			
			        CAST(ROUND(MIN(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevMinValue,
			
			        CAST(ROUND(MAX(CASE WHEN period_type = 'PREV'
			            THEN CAST([value] AS DECIMAL(10,2)) END), 1) AS DECIMAL(10,1)) AS prevMaxValue
			
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
            """, nativeQuery = true)
	List<MonthlySummaryProjection> sumMonthlyByNamesRaw(
			@Param("fac") String fac,
			@Param("month") String month,
			@Param("from") LocalDateTime from,
			@Param("currentTo") LocalDateTime currentTo,
			@Param("prevFrom") LocalDateTime prevFrom,
			@Param("prevTo") LocalDateTime prevTo,
			@Param("exchange") BigDecimal exchange,
			@Param("sepzone") BigDecimal sepzone
	);
}