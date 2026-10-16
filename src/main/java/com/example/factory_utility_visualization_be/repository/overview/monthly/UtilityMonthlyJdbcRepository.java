package com.example.factory_utility_visualization_be.repository.overview.monthly;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/*
 * Monthly summary cho FAC_A/B/C và KVH, dùng chung một batch SQL.
 *
 * Rẽ nhánh theo isKvh:
 * - Tập thiết bị (#Dev): fragment FAC_DEVICES / KVH_DEVICES.
 * - Kiểu cast giá trị đọc từ History: fragment để giữ đúng kiểu số của code cũ.
 * - Cách tính net điện: tham số :isKvh.
 *
 * History_Main chỉ được đọc một lần (một khoảng liên tục prevFrom -> currentTo),
 * gom ngay thành #Agg (vài trăm dòng), các bước sau chỉ đọc #Agg.
 *
 * SQL chỉ trả số gốc; usd/delta/total/solarShare tính ở UtilityMonthlyQueryService.
 */
@Repository
@RequiredArgsConstructor
public class UtilityMonthlyJdbcRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public record RawRow(
			String cate,
			String name,
			String unit,

			BigDecimal minValue,
			BigDecimal maxValue,
			BigDecimal prevMinValue,
			BigDecimal prevMaxValue,

			BigDecimal value,
			BigDecimal avgValue,
			BigDecimal vndCost,

			BigDecimal prevValue,
			BigDecimal prevAvgValue,
			BigDecimal prevVndCost,

			LocalDateTime pickAt,

			BigDecimal solarValue,
			BigDecimal prevSolarValue
	) {
	}

	/*
	 * FAC_A / FAC_B / FAC_C
	 *
	 * - Thiết bị thường: có channel box_id <> SOLAR thuộc scada của :fac.
	 *   FAC_A lấy thêm 'Sensor compressed air pressure Data' từ FAC_B.
	 * - Solar: channel box_id = SOLAR thuộc scada của :fac.
	 *   Không giữ unit (giống SolarTargetPara cũ).
	 * - Meter chính đã bao gồm Solar -> net = gross - solar ở bước sau.
	 */
	private static final String FAC_DEVICES = """
          SELECT
              pa.box_device_id,
              pa.plc_address,
              pa.name_en,
              pa.unit,
              CASE
                  WHEN pa.name_en = 'Total Energy Consumption' THEN 'ENERGY'
                  WHEN pa.name_en LIKE '%Cooling tank%' THEN 'COOLING'
                  WHEN pa.name_en = 'Data Pipeline pressure' THEN 'PIPELINE'
                  ELSE 'AIR'
              END AS kind
          FROM dbo.F2_Utility_Para pa
          WHERE EXISTS (
              SELECT 1
              FROM dbo.F2_Utility_Scada_Channel ch
              INNER JOIN dbo.F2_Utility_Scada sc
                  ON sc.scada_id = ch.scada_id
              WHERE ch.box_device_id = pa.box_device_id
                AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) <> 'SOLAR'
                AND (
                    (
                        UPPER(sc.fac) = UPPER(:fac)
                        AND (
                               pa.name_en = 'Total Energy Consumption'
                            OR pa.name_en LIKE '%Cooling tank%'
                            OR pa.name_en = 'Data Pipeline pressure'
                            OR pa.name_en = 'Sensor compressed air pressure Data'
                        )
                    )
                    OR (
                        UPPER(:fac) = 'FAC_A'
                        AND UPPER(sc.fac) = 'FAC_B'
                        AND pa.name_en = 'Sensor compressed air pressure Data'
                    )
                )
          )

          UNION ALL

          SELECT
              pa.box_device_id,
              pa.plc_address,
              pa.name_en,
              NULL,
              'SOLAR'
          FROM dbo.F2_Utility_Para pa
          WHERE pa.name_en = 'Total Energy Consumption'
            AND EXISTS (
                SELECT 1
                FROM dbo.F2_Utility_Scada_Channel ch
                INNER JOIN dbo.F2_Utility_Scada sc
                    ON sc.scada_id = ch.scada_id
                WHERE ch.box_device_id = pa.box_device_id
                  AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR'
                  AND UPPER(sc.fac) = UPPER(:fac)
            )
          """;

	/*
	 * KVH
	 *
	 * - Điện: Solar thắng -> box_device_id có bất kỳ mapping SOLAR nào
	 *   thì không thuộc GRID (nhưng vẫn phải tồn tại trong Scada Channel).
	 * - Water/Air: có channel box_id <> SOLAR.
	 * - Solar: giữ unit để không đổi cardinality so với code cũ.
	 */
	private static final String KVH_DEVICES = """
          SELECT
              pa.box_device_id,
              pa.plc_address,
              pa.name_en,
              pa.unit,
              CASE
                  WHEN pa.name_en = 'Total Energy Consumption' THEN 'ENERGY'
                  WHEN pa.name_en LIKE 'Cooling tank%' THEN 'COOLING'
                  WHEN pa.name_en = 'Data Pipeline pressure' THEN 'PIPELINE'
                  ELSE 'AIR'
              END AS kind
          FROM dbo.F2_Utility_Para pa
          WHERE (
                  pa.name_en = 'Total Energy Consumption'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.F2_Utility_Scada_Channel ch
                      WHERE ch.box_device_id = pa.box_device_id
                        AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR'
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.F2_Utility_Scada_Channel ch
                      WHERE ch.box_device_id = pa.box_device_id
                  )
              )
             OR (
                  (
                         pa.name_en LIKE 'Cooling tank%'
                      OR pa.name_en = 'Data Pipeline pressure'
                      OR pa.name_en = 'Sensor compressed air pressure Data'
                  )
                  AND EXISTS (
                      SELECT 1
                      FROM dbo.F2_Utility_Scada_Channel ch
                      WHERE ch.box_device_id = pa.box_device_id
                        AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) <> 'SOLAR'
                  )
              )

          UNION ALL

          SELECT
              pa.box_device_id,
              pa.plc_address,
              pa.name_en,
              pa.unit,
              'SOLAR'
          FROM dbo.F2_Utility_Para pa
          WHERE pa.name_en = 'Total Energy Consumption'
            AND EXISTS (
                SELECT 1
                FROM dbo.F2_Utility_Scada_Channel ch
                WHERE ch.box_device_id = pa.box_device_id
                  AND UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR'
            )
          """;

	/*
	 * Kiểu số giữ nguyên như code cũ:
	 * - FAC: điện SUM trên giá trị gốc, môi trường cast DECIMAL(18,4).
	 * - KVH: cả hai cast DECIMAL(19,6).
	 */
	private static final String FAC_ENERGY_VALUE = "h.[value]";
	private static final String FAC_ENV_VALUE = "CAST(h.[value] AS DECIMAL(18,4))";
	private static final String KVH_ENERGY_VALUE = "CAST(h.[value] AS DECIMAL(19,6))";
	private static final String KVH_ENV_VALUE = "CAST(h.[value] AS DECIMAL(19,6))";

	/*
	 * #Dev: DISTINCT theo thiết bị trước khi join History,
	 * để thiết bị map nhiều channel không bị nhân giá trị.
	 *
	 * #Agg: một lần đọc History_Main, gom theo
	 * - ENERGY: period, unit, WD, HourNumber
	 * - SOLAR : period, WD, HourNumber
	 * - COOLING / PIPELINE / AIR: period
	 *
	 * WD: '1' = Chủ nhật (19000101 là thứ Hai), không phụ thuộc SET DATEFIRST.
	 */
	private static final String PREPARE = """
          SET NOCOUNT ON;

          DROP TABLE IF EXISTS #Agg;
          DROP TABLE IF EXISTS #Dev;

          SELECT DISTINCT
              x.box_device_id,
              x.plc_address,
              x.name_en,
              x.unit,
              x.kind
          INTO #Dev
          FROM (
          {DEVICES}
          ) x;

          CREATE CLUSTERED INDEX CX_Dev
          ON #Dev(box_device_id, plc_address);

          SELECT
              d.kind,
              g.period,
              g.unit_key,
              g.WD,
              g.HourNumber,

              SUM(CASE WHEN d.kind = 'ENERGY'
                       THEN {ENERGY_VALUE} END) AS energy_sum,

              SUM(CASE WHEN d.kind = 'SOLAR'
                       THEN CAST(h.[value] AS DECIMAL(19,6)) END) AS solar_sum,

              AVG(CASE WHEN g.is_env = 1 THEN {ENV_VALUE} END) AS avg_value,
              MIN(CASE WHEN g.is_env = 1 THEN {ENV_VALUE} END) AS min_value,
              MAX(CASE WHEN g.is_env = 1 THEN {ENV_VALUE} END) AS max_value,

              MAX(d.unit) AS max_unit,
              MAX(h.pick_at) AS max_pick
          INTO #Agg
          FROM #Dev d
          INNER JOIN dbo.F2_Utility_Para_History_Main h
              ON h.box_device_id = d.box_device_id
             AND h.plc_address = d.plc_address
          CROSS APPLY (
              SELECT
                  CASE
                      WHEN h.pick_at >= :from THEN 'CURRENT'
                      WHEN h.pick_at < :prevTo THEN 'PREV'
                  END AS period,

                  CASE WHEN d.kind IN ('ENERGY', 'SOLAR') THEN 0 ELSE 1 END AS is_env,

                  CASE WHEN d.kind = 'ENERGY' THEN d.unit END AS unit_key,

                  CASE WHEN d.kind IN ('ENERGY', 'SOLAR') THEN
                      CASE
                          WHEN DATEDIFF(DAY, CAST('19000101' AS DATE), CAST(h.pick_at AS DATE)) % 7 = 6
                              THEN '1'
                          ELSE '2-7'
                      END
                  END AS WD,

                  CASE WHEN d.kind IN ('ENERGY', 'SOLAR')
                       THEN DATEPART(HOUR, h.pick_at) END AS HourNumber
          ) g
          WHERE h.pick_at >= :prevFrom
            AND h.pick_at < :currentTo
            AND h.[value] > 0
            AND g.period IS NOT NULL
            AND (g.is_env = 1 OR h.MTD = 'MTD')
          GROUP BY
              d.kind,
              g.period,
              g.unit_key,
              g.WD,
              g.HourNumber;

          DROP TABLE IF EXISTS #Dev;
          """;

	/*
	 * Electricity:
	 * - FAC: value = MAX(0, gross - solar),
	 *        vndCost = MAX(0, grossCost - solarCost) theo từng khung giờ tariff.
	 * - KVH: value = grid, vndCost = gridCost (không trừ Solar).
	 *
	 * Tariff hỗ trợ khung giờ qua nửa đêm.
	 *
	 * Water/Air: FAC luôn trả đủ 3 dòng (kể cả không có dữ liệu),
	 * KVH chỉ trả dòng có dữ liệu.
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
                  SUM(o.overlap * c.vnd) AS weighted_vnd_sum,
                  SUM(o.overlap) AS total_hours
              FROM dbo.F2_Utility_Cost_Master c
              CROSS JOIN Hours h
              CROSS APPLY (
                  SELECT
                      CASE
                          WHEN c.frTime < c.toTime
                              THEN IIF(c.toTime < h.n + 1, c.toTime, h.n + 1)
                                 - IIF(c.frTime > h.n, c.frTime, h.n)
                          WHEN h.n >= FLOOR(c.frTime)
                              THEN IIF(24.0 < h.n + 1, 24.0, h.n + 1)
                                 - IIF(c.frTime > h.n, c.frTime, h.n)
                          ELSE IIF(c.toTime < h.n + 1, c.toTime, h.n + 1)
                             - IIF(0.0 > h.n, 0.0, h.n)
                      END AS overlap
              ) o
              WHERE (
                      c.frTime < c.toTime
                      AND h.n < c.toTime
                      AND h.n + 1 > c.frTime
                    )
                 OR (
                      c.frTime > c.toTime
                      AND (h.n + 1 > c.frTime OR h.n < c.toTime)
                    )
              GROUP BY
                  c.WD,
                  h.n
          ),

          Rate AS (
              SELECT
                  WD,
                  HourNumber,
                  weighted_vnd_sum / NULLIF(total_hours, 0) AS vnd_rate
              FROM HourCost
          ),

          SolarMonthly AS (
              SELECT
                  CAST(COALESCE(SUM(CASE WHEN period = 'CURRENT' THEN solar_sum END), 0)
                       AS DECIMAL(18,2)) AS solarValue,
                  CAST(COALESCE(SUM(CASE WHEN period = 'PREV' THEN solar_sum END), 0)
                       AS DECIMAL(18,2)) AS prevSolarValue
              FROM #Agg
              WHERE kind = 'SOLAR'
          ),

          EnergyMonthly AS (
              SELECT
                  e.unit_key AS unit,

                  COALESCE(SUM(CASE WHEN e.period = 'CURRENT' THEN e.energy_sum END), 0) AS gross,
                  COALESCE(SUM(CASE WHEN e.period = 'PREV' THEN e.energy_sum END), 0) AS prevGross,

                  COALESCE(SUM(CASE WHEN e.period = 'CURRENT' THEN e.energy_sum * r.vnd_rate END), 0) AS grossCost,
                  COALESCE(SUM(CASE WHEN e.period = 'PREV' THEN e.energy_sum * r.vnd_rate END), 0) AS prevGrossCost,

                  COALESCE(SUM(CASE WHEN e.period = 'CURRENT'
                                    THEN COALESCE(s.solar_sum, 0) * r.vnd_rate ELSE 0 END), 0) AS solarCost,
                  COALESCE(SUM(CASE WHEN e.period = 'PREV'
                                    THEN COALESCE(s.solar_sum, 0) * r.vnd_rate ELSE 0 END), 0) AS prevSolarCost
              FROM #Agg e
              INNER JOIN Rate r
                  ON r.WD = e.WD
                 AND r.HourNumber = e.HourNumber
              LEFT JOIN #Agg s
                  ON s.kind = 'SOLAR'
                 AND s.period = e.period
                 AND s.WD = e.WD
                 AND s.HourNumber = e.HourNumber
              WHERE e.kind = 'ENERGY'
              GROUP BY e.unit_key
          ),

          EnergyNet AS (
              SELECT
                  e.unit,

                  CASE WHEN :isKvh = 1
                       THEN CAST(e.gross AS DECIMAL(18,2))
                       ELSE CAST(IIF(e.gross >= sm.solarValue, e.gross - sm.solarValue, 0)
                                 AS DECIMAL(18,2))
                  END AS value,

                  CASE WHEN :isKvh = 1
                       THEN CAST(e.prevGross AS DECIMAL(18,2))
                       ELSE CAST(IIF(e.prevGross >= sm.prevSolarValue, e.prevGross - sm.prevSolarValue, 0)
                                 AS DECIMAL(18,2))
                  END AS prevValue,

                  CASE WHEN :isKvh = 1
                       THEN CAST(e.grossCost AS DECIMAL(18,2))
                       ELSE CAST(CAST(IIF(e.grossCost >= e.solarCost, e.grossCost - e.solarCost, 0)
                                      AS DECIMAL(19,4)) AS DECIMAL(18,2))
                  END AS vndCost,

                  CASE WHEN :isKvh = 1
                       THEN CAST(e.prevGrossCost AS DECIMAL(18,2))
                       ELSE CAST(CAST(IIF(e.prevGrossCost >= e.prevSolarCost, e.prevGrossCost - e.prevSolarCost, 0)
                                      AS DECIMAL(19,4)) AS DECIMAL(18,2))
                  END AS prevVndCost,

                  sm.solarValue,
                  sm.prevSolarValue
              FROM EnergyMonthly e
              CROSS JOIN SolarMonthly sm
          ),

          EnvNames AS (
              SELECT v.kind, v.name, v.cate, v.ord
              FROM (
                  VALUES
                      ('COOLING', 'Cooling Tank Temperature', 'Water', 2),
                      ('PIPELINE', 'Data Pipeline pressure', 'Water', 3),
                      ('AIR', 'Sensor compressed air pressure Data', 'Compressed Air', 4)
              ) v(kind, name, cate, ord)
          ),

          EnvAgg AS (
              SELECT
                  n.ord,
                  n.cate,
                  n.name,
                  MAX(a.max_unit) AS unit,

                  CAST(ROUND(MAX(CASE WHEN a.period = 'CURRENT' THEN a.min_value END), 1) AS DECIMAL(18,1)) AS minValue,
                  CAST(ROUND(MAX(CASE WHEN a.period = 'CURRENT' THEN a.max_value END), 1) AS DECIMAL(18,1)) AS maxValue,
                  CAST(ROUND(MAX(CASE WHEN a.period = 'PREV' THEN a.min_value END), 1) AS DECIMAL(18,1)) AS prevMinValue,
                  CAST(ROUND(MAX(CASE WHEN a.period = 'PREV' THEN a.max_value END), 1) AS DECIMAL(18,1)) AS prevMaxValue,
                  CAST(ROUND(MAX(CASE WHEN a.period = 'CURRENT' THEN a.avg_value END), 1) AS DECIMAL(18,1)) AS avgValue,
                  CAST(ROUND(MAX(CASE WHEN a.period = 'PREV' THEN a.avg_value END), 1) AS DECIMAL(18,1)) AS prevAvgValue
              FROM EnvNames n
              LEFT JOIN #Agg a
                  ON a.kind = n.kind
              GROUP BY
                  n.kind,
                  n.ord,
                  n.cate,
                  n.name
              HAVING :isKvh = 0
                  OR COUNT(a.kind) > 0
          ),

          LastPick AS (
              SELECT MAX(max_pick) AS pickAt
              FROM #Agg
              WHERE period = 'CURRENT'
                AND kind <> 'SOLAR'
          ),

          FinalRows AS (
              SELECT
                  1 AS ord,
                  'Electricity' AS cate,
                  'Total Energy Consumption' AS name,
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
                  e.prevVndCost,

                  e.solarValue,
                  e.prevSolarValue
              FROM EnergyNet e

              UNION ALL

              SELECT
                  env.ord,
                  env.cate,
                  env.name,
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
                  CAST(NULL AS DECIMAL(18,2)),

                  CAST(NULL AS DECIMAL(18,2)),
                  CAST(NULL AS DECIMAL(18,2))
              FROM EnvAgg env
          )

          SELECT
              f.cate,
              f.name,
              f.unit,
              f.minValue,
              f.maxValue,
              f.prevMinValue,
              f.prevMaxValue,
              f.value,
              f.avgValue,
              f.vndCost,
              f.prevValue,
              f.prevAvgValue,
              f.prevVndCost,
              lp.pickAt,
              f.solarValue,
              f.prevSolarValue
          FROM FinalRows f
          CROSS JOIN LastPick lp
          ORDER BY f.ord
          OPTION (RECOMPILE);

          DROP TABLE IF EXISTS #Agg;
          """;

	private static final String FAC_SQL = buildSql(FAC_DEVICES, FAC_ENERGY_VALUE, FAC_ENV_VALUE);
	private static final String KVH_SQL = buildSql(KVH_DEVICES, KVH_ENERGY_VALUE, KVH_ENV_VALUE);

	private static String buildSql(String devices, String energyValue, String envValue) {
		return PREPARE
				.replace("{DEVICES}", devices)
				.replace("{ENERGY_VALUE}", energyValue)
				.replace("{ENV_VALUE}", envValue)
				+ "\n"
				+ FINAL_QUERY;
	}

	/*
	 * Toàn bộ batch (#Dev, #Agg, final SELECT) chạy trong một lần gọi JDBC
	 * nên bảng tạm luôn nằm trên cùng session.
	 */
	public List<RawRow> sumMonthly(
			String fac,
			boolean kvh,
			LocalDateTime from,
			LocalDateTime currentTo,
			LocalDateTime prevFrom,
			LocalDateTime prevTo
	) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("fac", fac)
				.addValue("isKvh", kvh ? 1 : 0)
				.addValue("from", Timestamp.valueOf(from))
				.addValue("currentTo", Timestamp.valueOf(currentTo))
				.addValue("prevFrom", Timestamp.valueOf(prevFrom))
				.addValue("prevTo", Timestamp.valueOf(prevTo));

		return List.copyOf(
				jdbc.query(kvh ? KVH_SQL : FAC_SQL, params, (rs, rowNum) -> mapRow(rs))
		);
	}

	private static RawRow mapRow(ResultSet rs) throws SQLException {
		Timestamp pickAt = rs.getTimestamp("pickAt");

		return new RawRow(
				rs.getString("cate"),
				rs.getString("name"),
				rs.getString("unit"),

				rs.getBigDecimal("minValue"),
				rs.getBigDecimal("maxValue"),
				rs.getBigDecimal("prevMinValue"),
				rs.getBigDecimal("prevMaxValue"),

				rs.getBigDecimal("value"),
				rs.getBigDecimal("avgValue"),
				rs.getBigDecimal("vndCost"),

				rs.getBigDecimal("prevValue"),
				rs.getBigDecimal("prevAvgValue"),
				rs.getBigDecimal("prevVndCost"),

				pickAt == null ? null : pickAt.toLocalDateTime(),

				rs.getBigDecimal("solarValue"),
				rs.getBigDecimal("prevSolarValue")
		);
	}
}
