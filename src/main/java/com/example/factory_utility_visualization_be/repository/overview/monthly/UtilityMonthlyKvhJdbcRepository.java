package com.example.factory_utility_visualization_be.repository.overview.monthly;

import com.example.factory_utility_visualization_be.repository.overview.monthly.projection.MonthlySummaryProjection;
import com.example.factory_utility_visualization_be.repository.overview.monthly.projection.MonthlySummaryProjectionRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UtilityMonthlyKvhJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	private static final String DROP_TEMP_TABLES = """
          DROP TABLE IF EXISTS #NormalDevices;
          DROP TABLE IF EXISTS #SolarDevices;
          DROP TABLE IF EXISTS #EnergyHourly;
          DROP TABLE IF EXISTS #SolarHourly;
          DROP TABLE IF EXISTS #EnvironmentAgg;
          DROP TABLE IF EXISTS #LastPick;
          """;

	private static final String CREATE_NORMAL_DEVICES = """
          SELECT DISTINCT
              pa.box_device_id,
              pa.plc_address,
              pa.name_en,
              pa.unit,
              CASE
                  WHEN pa.name_en = 'Total Energy Consumption'
                      THEN 'Electricity'
                  WHEN pa.name_en LIKE 'Cooling tank%'
                      THEN 'Water'
                  WHEN pa.name_en = 'Data Pipeline pressure'
                      THEN 'Water'
                  WHEN pa.name_en =
                       'Sensor compressed air pressure Data'
                      THEN 'Compressed Air'
              END AS cate
          INTO #NormalDevices
          FROM dbo.F2_Utility_Para pa
          WHERE (
                 pa.name_en = 'Total Energy Consumption'
              OR pa.name_en LIKE 'Cooling tank%'
              OR pa.name_en = 'Data Pipeline pressure'
              OR pa.name_en =
                 'Sensor compressed air pressure Data'
          )
          AND EXISTS (
              SELECT 1
              FROM dbo.F2_Utility_Scada_Channel ch
              WHERE ch.box_device_id = pa.box_device_id
                AND UPPER(
                        LTRIM(
                            RTRIM(
                                ISNULL(ch.box_id, '')
                            )
                        )
                    ) <> 'SOLAR'
          );

          CREATE CLUSTERED INDEX CX_NormalDevices
          ON #NormalDevices(
              box_device_id,
              plc_address,
              name_en,
              unit
          );
          """;

	/*
	 * IMPORTANT:
	 *
	 * Giữ unit trong #SolarDevices.
	 *
	 * Repo OLD tạo ParaDedup theo:
	 *   box_device_id, plc_address, name_en, unit
	 *
	 * rồi SolarTargetPara bỏ unit ở SELECT.
	 *
	 * Nếu tương lai một solar address có >1 unit khác nhau,
	 * OLD sẽ tạo >1 row và History cũng bị nhân tương ứng.
	 *
	 * Giữ unit ở temp table giúp migration không vô tình thay đổi
	 * cardinality của OLD semantics.
	 */
	private static final String CREATE_SOLAR_DEVICES = """
          SELECT DISTINCT
              pa.box_device_id,
              pa.plc_address,
              pa.unit
          INTO #SolarDevices
          FROM dbo.F2_Utility_Para pa
          WHERE pa.name_en = 'Total Energy Consumption'
            AND EXISTS (
                SELECT 1
                FROM dbo.F2_Utility_Scada_Channel ch
                WHERE ch.box_device_id = pa.box_device_id
                  AND UPPER(
                          LTRIM(
                              RTRIM(
                                  ISNULL(ch.box_id, '')
                              )
                          )
                      ) = 'SOLAR'
            );

          CREATE CLUSTERED INDEX CX_SolarDevices
          ON #SolarDevices(
              box_device_id,
              plc_address,
              unit
          );
          """;

	private static final String CREATE_ENERGY_HOURLY = """
          SELECT
              x.period_type,
              x.unit,
              x.WD,
              x.HourNumber,
              SUM(x.[value]) AS hour_value
          INTO #EnergyHourly
          FROM (
              SELECT
                  'CURRENT' AS period_type,
                  d.unit,
                  CASE
                      WHEN DATEPART(
                          WEEKDAY,
                          CAST(h.pick_at AS DATE)
                      ) = 1
                          THEN '1'
                      ELSE '2-7'
                  END AS WD,
                  DATEPART(HOUR, h.pick_at) AS HourNumber,
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  ) AS [value]
              FROM #NormalDevices d
              INNER JOIN dbo.F2_Utility_Para_History_Main h
                  ON h.box_device_id = d.box_device_id
                 AND h.plc_address = d.plc_address
              WHERE d.name_en = 'Total Energy Consumption'
                AND h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0
                AND h.MTD = 'MTD'

              UNION ALL

              SELECT
                  'PREV',
                  d.unit,
                  CASE
                      WHEN DATEPART(
                          WEEKDAY,
                          CAST(h.pick_at AS DATE)
                      ) = 1
                          THEN '1'
                      ELSE '2-7'
                  END,
                  DATEPART(HOUR, h.pick_at),
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  )
              FROM #NormalDevices d
              INNER JOIN dbo.F2_Utility_Para_History_Main h
                  ON h.box_device_id = d.box_device_id
                 AND h.plc_address = d.plc_address
              WHERE d.name_en = 'Total Energy Consumption'
                AND h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0
                AND h.MTD = 'MTD'
          ) x
          GROUP BY
              x.period_type,
              x.unit,
              x.WD,
              x.HourNumber;

          CREATE CLUSTERED INDEX CX_EnergyHourly
          ON #EnergyHourly(
              period_type,
              WD,
              HourNumber,
              unit
          );
          """;

	private static final String CREATE_SOLAR_HOURLY = """
          SELECT
              x.period_type,
              x.WD,
              x.HourNumber,
              SUM(x.[value]) AS solar_hour_value
          INTO #SolarHourly
          FROM (
              SELECT
                  'CURRENT' AS period_type,
                  CASE
                      WHEN DATEPART(
                          WEEKDAY,
                          CAST(h.pick_at AS DATE)
                      ) = 1
                          THEN '1'
                      ELSE '2-7'
                  END AS WD,
                  DATEPART(HOUR, h.pick_at) AS HourNumber,
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  ) AS [value]
              FROM #SolarDevices d
              INNER JOIN dbo.F2_Utility_Para_History_Main h
                  ON h.box_device_id = d.box_device_id
                 AND h.plc_address = d.plc_address
              WHERE h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0
                AND h.MTD = 'MTD'

              UNION ALL

              SELECT
                  'PREV',
                  CASE
                      WHEN DATEPART(
                          WEEKDAY,
                          CAST(h.pick_at AS DATE)
                      ) = 1
                          THEN '1'
                      ELSE '2-7'
                  END,
                  DATEPART(HOUR, h.pick_at),
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  )
              FROM #SolarDevices d
              INNER JOIN dbo.F2_Utility_Para_History_Main h
                  ON h.box_device_id = d.box_device_id
                 AND h.plc_address = d.plc_address
              WHERE h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0
                AND h.MTD = 'MTD'
          ) x
          GROUP BY
              x.period_type,
              x.WD,
              x.HourNumber;

          CREATE CLUSTERED INDEX CX_SolarHourly
          ON #SolarHourly(
              period_type,
              WD,
              HourNumber
          );
          """;

	/*
	 * Production-safe Environment:
	 *
	 * Aggregate trực tiếp toàn bộ raw History theo business bucket.
	 * Không:
	 *
	 *     AVG(AVG(...))
	 *
	 * nên vẫn giữ đúng semantics của OLD Base -> Water/Air aggregate.
	 */
	private static final String CREATE_ENVIRONMENT_AGG = """
          SELECT
              CASE
                  WHEN d.name_en LIKE 'Cooling tank%'
                      THEN 'Cooling Tank Temperature'
                  WHEN d.name_en = 'Data Pipeline pressure'
                      THEN 'Data Pipeline pressure'
                  WHEN d.name_en =
                       'Sensor compressed air pressure Data'
                      THEN 'Sensor compressed air pressure Data'
              END AS name,

              CASE
                  WHEN d.name_en LIKE 'Cooling tank%'
                      THEN 'Water'
                  WHEN d.name_en = 'Data Pipeline pressure'
                      THEN 'Water'
                  WHEN d.name_en =
                       'Sensor compressed air pressure Data'
                      THEN 'Compressed Air'
              END AS cate,

              MAX(d.unit) AS unit,

              CAST(
                  ROUND(
                      AVG(
                          CASE
                              WHEN p.period_type = 'CURRENT'
                                  THEN p.[value]
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
                              WHEN p.period_type = 'CURRENT'
                                  THEN p.[value]
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
                              WHEN p.period_type = 'CURRENT'
                                  THEN p.[value]
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
                              WHEN p.period_type = 'PREV'
                                  THEN p.[value]
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
                              WHEN p.period_type = 'PREV'
                                  THEN p.[value]
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
                              WHEN p.period_type = 'PREV'
                                  THEN p.[value]
                          END
                      ),
                      1
                  )
                  AS DECIMAL(18,1)
              ) AS prevMaxValue

          INTO #EnvironmentAgg

          FROM #NormalDevices d

          INNER JOIN (
              SELECT
                  'CURRENT' AS period_type,
                  h.box_device_id,
                  h.plc_address,
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  ) AS [value]
              FROM dbo.F2_Utility_Para_History_Main h
              WHERE h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0

              UNION ALL

              SELECT
                  'PREV',
                  h.box_device_id,
                  h.plc_address,
                  CAST(
                      h.[value] AS DECIMAL(19,6)
                  )
              FROM dbo.F2_Utility_Para_History_Main h
              WHERE h.pick_at >= ?
                AND h.pick_at < ?
                AND h.[value] > 0
          ) p
              ON p.box_device_id = d.box_device_id
             AND p.plc_address = d.plc_address

          WHERE d.name_en <> 'Total Energy Consumption'

          GROUP BY
              CASE
                  WHEN d.name_en LIKE 'Cooling tank%'
                      THEN 'Cooling Tank Temperature'
                  WHEN d.name_en = 'Data Pipeline pressure'
                      THEN 'Data Pipeline pressure'
                  WHEN d.name_en =
                       'Sensor compressed air pressure Data'
                      THEN 'Sensor compressed air pressure Data'
              END,
              CASE
                  WHEN d.name_en LIKE 'Cooling tank%'
                      THEN 'Water'
                  WHEN d.name_en = 'Data Pipeline pressure'
                      THEN 'Water'
                  WHEN d.name_en =
                       'Sensor compressed air pressure Data'
                      THEN 'Compressed Air'
              END;
          """;

	private static final String CREATE_LAST_PICK = """
          SELECT
              MAX(h.pick_at) AS pickAt
          INTO #LastPick
          FROM #NormalDevices d
          INNER JOIN dbo.F2_Utility_Para_History_Main h
              ON h.box_device_id = d.box_device_id
             AND h.plc_address = d.plc_address
          WHERE h.pick_at >= ?
            AND h.pick_at < ?
            AND h.[value] > 0
            AND (
                d.cate <> 'Electricity'
                OR h.MTD = 'MTD'
            );
          """;

	/*
	 * Tariff giữ nguyên business của repo OLD:
	 *
	 * - WD = 1 / 2-7
	 * - hỗ trợ tariff crossing midnight
	 * - gross cost theo hourly rate
	 * - solar cost theo đúng hourly rate
	 * - grid = MAX(gross - solar, 0)
	 */
	private static final String FINAL_QUERY = """
          ;WITH Hours AS (
              SELECT v.n
              FROM (
                  VALUES
                      (0),(1),(2),(3),(4),(5),
                      (6),(7),(8),(9),(10),(11),
                      (12),(13),(14),(15),(16),(17),
                      (18),(19),(20),(21),(22),(23)
              ) v(n)
          ),

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

              WHERE (
                      c.frTime < c.toTime
                      AND h.n < c.toTime
                      AND h.n + 1 > c.frTime
                    )
                 OR (
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

          Rate AS (
              SELECT
                  WD,
                  HourNumber,
                  weighted_vnd_sum
                      / NULLIF(total_hours, 0) AS vnd_rate
              FROM HourCost
          ),

          SolarMonthly AS (
              SELECT
                  CAST(
                      COALESCE(
                          SUM(
                              CASE
                                  WHEN period_type = 'CURRENT'
                                      THEN solar_hour_value
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
                                  WHEN period_type = 'PREV'
                                      THEN solar_hour_value
                                  ELSE 0
                              END
                          ),
                          0
                      )
                      AS DECIMAL(18,2)
                  ) AS prevSolarValue

              FROM #SolarHourly
          ),

          EnergyMonthly AS (
              SELECT
                  e.unit,

                  SUM(
                      CASE
                          WHEN e.period_type = 'CURRENT'
                              THEN e.hour_value
                          ELSE 0
                      END
                  ) AS grossValue,

                  SUM(
                      CASE
                          WHEN e.period_type = 'PREV'
                              THEN e.hour_value
                          ELSE 0
                      END
                  ) AS prevGrossValue,

                  SUM(
                      CASE
                          WHEN e.period_type = 'CURRENT'
                              THEN e.hour_value * r.vnd_rate
                          ELSE 0
                      END
                  ) AS grossVndCost,

                  SUM(
                      CASE
                          WHEN e.period_type = 'PREV'
                              THEN e.hour_value * r.vnd_rate
                          ELSE 0
                      END
                  ) AS prevGrossVndCost,

                  SUM(
                      CASE
                          WHEN e.period_type = 'CURRENT'
                              THEN COALESCE(
                                  s.solar_hour_value,
                                  0
                              ) * r.vnd_rate
                          ELSE 0
                      END
                  ) AS solarVndCost,

                  SUM(
                      CASE
                          WHEN e.period_type = 'PREV'
                              THEN COALESCE(
                                  s.solar_hour_value,
                                  0
                              ) * r.vnd_rate
                          ELSE 0
                      END
                  ) AS prevSolarVndCost

              FROM #EnergyHourly e

              INNER JOIN Rate r
                  ON r.WD = e.WD
                 AND r.HourNumber = e.HourNumber

              LEFT JOIN #SolarHourly s
                  ON s.period_type = e.period_type
                 AND s.WD = e.WD
                 AND s.HourNumber = e.HourNumber

              GROUP BY e.unit
          ),

          EnergyNet AS (
              SELECT
                  'Total Energy Consumption' AS name,
                  'Electricity' AS cate,
                  e.unit,

                  CAST(
                      CASE
                          WHEN COALESCE(e.grossValue, 0)
                               > COALESCE(s.solarValue, 0)
                          THEN
                              COALESCE(e.grossValue, 0)
                              - COALESCE(s.solarValue, 0)
                          ELSE 0
                      END
                      AS DECIMAL(18,2)
                  ) AS value,

                  CAST(
                      CASE
                          WHEN COALESCE(e.prevGrossValue, 0)
                               > COALESCE(s.prevSolarValue, 0)
                          THEN
                              COALESCE(e.prevGrossValue, 0)
                              - COALESCE(s.prevSolarValue, 0)
                          ELSE 0
                      END
                      AS DECIMAL(18,2)
                  ) AS prevValue,

                  CAST(
                      CASE
                          WHEN COALESCE(e.grossVndCost, 0)
                               > COALESCE(e.solarVndCost, 0)
                          THEN
                              COALESCE(e.grossVndCost, 0)
                              - COALESCE(e.solarVndCost, 0)
                          ELSE 0
                      END
                      AS DECIMAL(18,2)
                  ) AS vndCost,

                  CAST(
                      CASE
                          WHEN COALESCE(e.prevGrossVndCost, 0)
                               > COALESCE(e.prevSolarVndCost, 0)
                          THEN
                              COALESCE(e.prevGrossVndCost, 0)
                              - COALESCE(e.prevSolarVndCost, 0)
                          ELSE 0
                      END
                      AS DECIMAL(18,2)
                  ) AS prevVndCost

              FROM EnergyMonthly e
              CROSS JOIN SolarMonthly s
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

                  e.value,
                  CAST(NULL AS DECIMAL(18,1)) AS avgValue,

                  e.vndCost,
                  e.prevValue,

                  CAST(NULL AS DECIMAL(18,1)) AS prevAvgValue,
                  e.prevVndCost

              FROM EnergyNet e

              UNION ALL

              SELECT
                  env.name,
                  env.cate,
                  env.unit,

                  env.minValue,
                  env.maxValue,
                  env.prevMinValue,
                  env.prevMaxValue,

                  CAST(NULL AS DECIMAL(18,2)),
                  env.avgValue,

                  CAST(NULL AS DECIMAL(18,2)),
                  CAST(NULL AS DECIMAL(18,2)),

                  env.prevAvgValue,
                  CAST(NULL AS DECIMAL(18,2))

              FROM #EnvironmentAgg env
          )

          SELECT
              f.cate AS cate,
              f.name AS name,
              CAST(? AS VARCHAR(6)) AS [month],

              f.minValue AS minValue,
              f.maxValue AS maxValue,
              f.prevMinValue AS prevMinValue,
              f.prevMaxValue AS prevMaxValue,

              f.value AS value,
              f.avgValue AS avgValue,

              f.vndCost AS vndCost,

              CAST(
                  f.vndCost
                  / NULLIF(?, 0)
                  * ?
                  AS DECIMAL(18,2)
              ) AS usdCost,

              f.prevValue AS prevValue,
              f.prevAvgValue AS prevAvgValue,

              f.prevVndCost AS prevVndCost,

              CAST(
                  f.prevVndCost
                  / NULLIF(?, 0)
                  * ?
                  AS DECIMAL(18,2)
              ) AS prevUsdCost,

              CAST(
                  COALESCE(f.value, f.avgValue, 0)
                  - COALESCE(
                      f.prevValue,
                      f.prevAvgValue,
                      0
                  )
                  AS DECIMAL(18,2)
              ) AS deltaValue,

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
                          * 100
                  END
                  AS DECIMAL(10,2)
              ) AS deltaPercent,

              f.unit AS unit,
              lp.pickAt AS pickAt,

              CASE
                  WHEN f.cate = 'Electricity'
                      THEN sm.solarValue
              END AS solarValue,

              CASE
                  WHEN f.cate = 'Electricity'
                      THEN sm.prevSolarValue
              END AS prevSolarValue,

              CASE
                  WHEN f.cate = 'Electricity'
                  THEN CAST(
                      COALESCE(f.value, 0)
                      + COALESCE(sm.solarValue, 0)
                      AS DECIMAL(18,2)
                  )
              END AS totalEnergyValue,

              CASE
                  WHEN f.cate = 'Electricity'
                  THEN CAST(
                      COALESCE(f.prevValue, 0)
                      + COALESCE(sm.prevSolarValue, 0)
                      AS DECIMAL(18,2)
                  )
              END AS prevTotalEnergyValue,

              CASE
                  WHEN f.cate = 'Electricity'
                   AND (
                       COALESCE(f.value, 0)
                       + COALESCE(sm.solarValue, 0)
                   ) > 0
                  THEN CAST(
                      COALESCE(sm.solarValue, 0)
                      /
                      NULLIF(
                          COALESCE(f.value, 0)
                          + COALESCE(sm.solarValue, 0),
                          0
                      )
                      * 100
                      AS DECIMAL(10,2)
                  )
              END AS solarSharePercent

          FROM FinalRows f
          CROSS JOIN #LastPick lp
          CROSS JOIN SolarMonthly sm

          ORDER BY
              CASE
                  WHEN f.cate = 'Electricity'
                      THEN 1
                  WHEN f.cate = 'Water'
                   AND f.name = 'Cooling Tank Temperature'
                      THEN 2
                  WHEN f.cate = 'Water'
                   AND f.name = 'Data Pipeline pressure'
                      THEN 3
                  WHEN f.cate = 'Compressed Air'
                      THEN 4
                  ELSE 9
              END

          OPTION (RECOMPILE);
          """;

	public List<MonthlySummaryProjection> sumMonthlyKvhRaw(
			String month,
			LocalDateTime from,
			LocalDateTime currentTo,
			LocalDateTime prevFrom,
			LocalDateTime prevTo,
			BigDecimal exchange,
			BigDecimal sepzone
	) {
		return jdbcTemplate.execute(
				(ConnectionCallback<List<MonthlySummaryProjection>>) connection -> {

					String sql = """
                    SET NOCOUNT ON;

                    DROP TABLE IF EXISTS #NormalDevices;
                    DROP TABLE IF EXISTS #SolarDevices;
                    DROP TABLE IF EXISTS #EnergyHourly;
                    DROP TABLE IF EXISTS #SolarHourly;
                    DROP TABLE IF EXISTS #EnvironmentAgg;
                    DROP TABLE IF EXISTS #LastPick;

                    """
							+ CREATE_NORMAL_DEVICES
							+ "\n"
							+ CREATE_SOLAR_DEVICES
							+ "\n"
							+ CREATE_ENERGY_HOURLY
							+ "\n"
							+ CREATE_SOLAR_HOURLY
							+ "\n"
							+ CREATE_ENVIRONMENT_AGG
							+ "\n"
							+ CREATE_LAST_PICK
							+ "\n"
							+ FINAL_QUERY;

					try (PreparedStatement ps =
							     connection.prepareStatement(sql)) {

						int i = 1;

						// ==========================================
						// CREATE_ENERGY_HOURLY
						// ==========================================
						ps.setTimestamp(i++, Timestamp.valueOf(from));
						ps.setTimestamp(i++, Timestamp.valueOf(currentTo));
						ps.setTimestamp(i++, Timestamp.valueOf(prevFrom));
						ps.setTimestamp(i++, Timestamp.valueOf(prevTo));

						// ==========================================
						// CREATE_SOLAR_HOURLY
						// ==========================================
						ps.setTimestamp(i++, Timestamp.valueOf(from));
						ps.setTimestamp(i++, Timestamp.valueOf(currentTo));
						ps.setTimestamp(i++, Timestamp.valueOf(prevFrom));
						ps.setTimestamp(i++, Timestamp.valueOf(prevTo));

						// ==========================================
						// CREATE_ENVIRONMENT_AGG
						// ==========================================
						ps.setTimestamp(i++, Timestamp.valueOf(from));
						ps.setTimestamp(i++, Timestamp.valueOf(currentTo));
						ps.setTimestamp(i++, Timestamp.valueOf(prevFrom));
						ps.setTimestamp(i++, Timestamp.valueOf(prevTo));

						// ==========================================
						// CREATE_LAST_PICK
						// ==========================================
						ps.setTimestamp(i++, Timestamp.valueOf(from));
						ps.setTimestamp(i++, Timestamp.valueOf(currentTo));

						// ==========================================
						// FINAL_QUERY
						// ==========================================
						ps.setString(i++, month);

						ps.setBigDecimal(i++, exchange);
						ps.setBigDecimal(i++, sepzone);

						ps.setBigDecimal(i++, exchange);
						ps.setBigDecimal(i, sepzone);

						try (ResultSet rs = ps.executeQuery()) {
							List<MonthlySummaryProjection> result =
									new ArrayList<>();

							while (rs.next()) {
								result.add(mapRow(rs));
							}

							return List.copyOf(result);
						}
					} finally {
						/*
						 * Cleanup riêng bằng Statement trên cùng connection.
						 *
						 * Nếu batch thành công, #temp sẽ bị xóa.
						 * Nếu batch fail giữa chừng, vẫn cleanup trước khi
						 * Hikari trả connection về pool.
						 */
						try (Statement st = connection.createStatement()) {
							st.execute("""
                            DROP TABLE IF EXISTS #LastPick;
                            DROP TABLE IF EXISTS #EnvironmentAgg;
                            DROP TABLE IF EXISTS #SolarHourly;
                            DROP TABLE IF EXISTS #EnergyHourly;
                            DROP TABLE IF EXISTS #SolarDevices;
                            DROP TABLE IF EXISTS #NormalDevices;
                            """);
						} catch (SQLException ignored) {
							// Không che exception gốc.
						}
					}
				}
		);
	}



	private MonthlySummaryProjectionRow mapRow(
			ResultSet rs
	) throws SQLException {

		Timestamp pickAt = rs.getTimestamp("pickAt");

		return new MonthlySummaryProjectionRow(
				rs.getString("name"),
				rs.getString("cate"),
				rs.getString("unit"),
				rs.getString("month"),

				rs.getBigDecimal("minValue"),
				rs.getBigDecimal("maxValue"),
				rs.getBigDecimal("prevMinValue"),
				rs.getBigDecimal("prevMaxValue"),

				rs.getBigDecimal("value"),
				rs.getBigDecimal("avgValue"),

				rs.getBigDecimal("vndCost"),
				rs.getBigDecimal("usdCost"),

				rs.getBigDecimal("prevValue"),
				rs.getBigDecimal("prevAvgValue"),

				rs.getBigDecimal("prevVndCost"),
				rs.getBigDecimal("prevUsdCost"),

				rs.getBigDecimal("deltaValue"),
				rs.getBigDecimal("deltaPercent"),

				pickAt == null
						? null
						: pickAt.toLocalDateTime(),

				rs.getBigDecimal("solarValue"),
				rs.getBigDecimal("prevSolarValue"),

				rs.getBigDecimal("totalEnergyValue"),
				rs.getBigDecimal("prevTotalEnergyValue"),

				rs.getBigDecimal("solarSharePercent")
		);
	}
}