package com.example.factory_utility_visualization_be.repository.overview.monthly;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * SQL chỉ trả số gốc (kWh, không có tiền); VND/USD/delta/total/solarShare
 * tính ở UtilityMonthlyQueryService (tiền qua ElectricityPricingService).
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

			BigDecimal prevValue,
			BigDecimal prevAvgValue,

			LocalDateTime pickAt,

			BigDecimal solarValue,
			BigDecimal prevSolarValue
	) {
	}

	/**
	 * kWh điện lưới của một giờ.
	 *
	 * @param period 'CURRENT' | 'PREV'
	 * @param hourAt EnergyDay + HourNumber (giờ bắt đầu, = pick_at - 1h)
	 * @param unit   unit của dòng summary Electricity tương ứng
	 */
	public record EnergyHour(String period, LocalDateTime hourAt, String unit, BigDecimal kwh) {
	}

	public record MonthlyResult(List<RawRow> rows, List<EnergyHour> energyHours) {
	}

	/*
	 * FAC_A / FAC_B / FAC_C
	 *
	 * - Thiết bị thường: có channel box_id <> SOLAR thuộc scada của :fac.
	 *   FAC_A lấy thêm 'Sensor compressed air pressure Data' từ FAC_B.
	 * - Điện lưới giống KVH và mọi API điện khác: Solar thắng -> meter có bất
	 *   kỳ channel SOLAR nào không thuộc GRID; value = điện lưới, không trừ Solar.
	 * - Solar: channel box_id = SOLAR thuộc scada của :fac.
	 *   Không giữ unit (giống SolarTargetPara cũ).
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
            AND NOT (
                pa.name_en = 'Total Energy Consumption'
                AND EXISTS (
                    SELECT 1
                    FROM dbo.F2_Utility_Scada_Channel chs
                    WHERE chs.box_device_id = pa.box_device_id
                      AND UPPER(LTRIM(RTRIM(ISNULL(chs.box_id, '')))) = 'SOLAR'
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
	 * Meter trong :excludedMeters (trùng, vd P-3.1 = P-3) không tính điện.
	 *
	 * #Agg: một lần đọc History_Main, gom theo
	 * - ENERGY: period, unit, EnergyDay, HourNumber (giờ để định giá ở Java)
	 * - SOLAR : period
	 * - COOLING / PIPELINE / AIR: period
	 *
	 * ENERGY / SOLAR: dòng History_Main là điện của giờ KẾT THÚC tại pick_at,
	 * nên giờ / ngày / kỳ tính theo ts = pick_at - 1h (giống power-usage,
	 * electric-consumption); môi trường giữ ts = pick_at.
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
          ) x
          WHERE x.kind NOT IN ('ENERGY', 'SOLAR')
             OR UPPER(LTRIM(RTRIM(x.box_device_id))) NOT IN (:excludedMeters);

          CREATE CLUSTERED INDEX CX_Dev
          ON #Dev(box_device_id, plc_address);

          SELECT
              d.kind,
              g.period,
              g.unit_key,
              g.HourNumber,
              g.EnergyDay,

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
                  CASE WHEN d.kind IN ('ENERGY', 'SOLAR')
                       THEN DATEADD(HOUR, -1, h.pick_at)
                       ELSE h.pick_at
                  END AS ts
          ) t
          CROSS APPLY (
              SELECT
                  CASE
                      WHEN t.ts >= :from THEN 'CURRENT'
                      WHEN t.ts < :prevTo THEN 'PREV'
                  END AS period,

                  CASE WHEN d.kind IN ('ENERGY', 'SOLAR') THEN 0 ELSE 1 END AS is_env,

                  CASE WHEN d.kind = 'ENERGY' THEN d.unit END AS unit_key,

                  -- hour of the energy (ENERGY only): priced in Java
                  -- (ElectricityPricingService) at EnergyDay + HourNumber
                  CASE WHEN d.kind = 'ENERGY'
                       THEN DATEPART(HOUR, t.ts) END AS HourNumber,

                  CASE WHEN d.kind = 'ENERGY'
                       THEN CAST(t.ts AS DATE) END AS EnergyDay
          ) g
          WHERE h.pick_at >= :prevFrom
            AND h.pick_at < DATEADD(HOUR, 1, :currentTo)
            AND t.ts >= :prevFrom
            AND t.ts < :currentTo
            AND h.[value] > 0
            AND g.period IS NOT NULL
            AND (g.is_env = 1 OR h.MTD = 'MTD')
          GROUP BY
              d.kind,
              g.period,
              g.unit_key,
              g.HourNumber,
              g.EnergyDay;

          DROP TABLE IF EXISTS #Dev;
          """;

	/*
	 * Electricity (FAC và KVH như nhau, giống mọi API điện khác):
	 * - value   = điện lưới (meter không có channel SOLAR), không trừ Solar.
	 * - Tiền (VND/USD) KHÔNG tính ở SQL: ENERGY_HOURS_QUERY trả kWh theo
	 *   (period, EnergyDay, HourNumber, unit), UtilityMonthlyQueryService
	 *   định giá bằng ElectricityPricingService.
	 *
	 * Water/Air: FAC luôn trả đủ 3 dòng (kể cả không có dữ liệu),
	 * KVH chỉ trả dòng có dữ liệu.
	 */
	private static final String FINAL_QUERY = """
          ;WITH SolarMonthly AS (
              SELECT
                  CAST(COALESCE(SUM(CASE WHEN period = 'CURRENT' THEN solar_sum END), 0)
                       AS DECIMAL(18,2)) AS solarValue,
                  CAST(COALESCE(SUM(CASE WHEN period = 'PREV' THEN solar_sum END), 0)
                       AS DECIMAL(18,2)) AS prevSolarValue
              FROM #Agg
              WHERE kind = 'SOLAR'
          ),

          EnergyTotal AS (
              SELECT
                  e.unit_key AS unit,
                  COALESCE(SUM(CASE WHEN e.period = 'CURRENT' THEN e.energy_sum END), 0) AS gross,
                  COALESCE(SUM(CASE WHEN e.period = 'PREV' THEN e.energy_sum END), 0) AS prevGross
              FROM #Agg e
              WHERE e.kind = 'ENERGY'
              GROUP BY e.unit_key
          ),

          EnergyNet AS (
              SELECT
                  e.unit,
                  CAST(e.gross AS DECIMAL(18,2)) AS value,
                  CAST(e.prevGross AS DECIMAL(18,2)) AS prevValue,
                  sm.solarValue,
                  sm.prevSolarValue
              FROM EnergyTotal e
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

                  e.prevValue,
                  CAST(NULL AS DECIMAL(18,1)) AS prevAvgValue,

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
                  env.prevAvgValue,

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
              f.prevValue,
              f.prevAvgValue,
              lp.pickAt,
              f.solarValue,
              f.prevSolarValue
          FROM FinalRows f
          CROSS JOIN LastPick lp
          ORDER BY f.ord
          OPTION (RECOMPILE);
          """;

	/*
	 * Result set thứ 2 của batch: kWh điện lưới theo
	 * (period, EnergyDay, HourNumber, unit) để định giá ở Java.
	 */
	private static final String ENERGY_HOURS_QUERY = """
          ;WITH EnergyMonthly AS (
              SELECT
                  e.period,
                  e.EnergyDay,
                  e.HourNumber,
                  e.unit_key AS unit,
                  SUM(e.energy_sum) AS kwh
              FROM #Agg e
              WHERE e.kind = 'ENERGY'
              GROUP BY
                  e.period,
                  e.EnergyDay,
                  e.HourNumber,
                  e.unit_key
          )
          SELECT
              period,
              CAST(EnergyDay AS DATETIME) AS energyDay,
              HourNumber AS hourNumber,
              unit,
              kwh
          FROM EnergyMonthly
          ORDER BY period, EnergyDay, HourNumber;

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
				+ FINAL_QUERY
				+ "\n"
				+ ENERGY_HOURS_QUERY;
	}

	/*
	 * Toàn bộ batch (#Dev, #Agg, 2 SELECT) chạy trong một lần gọi JDBC
	 * nên bảng tạm luôn nằm trên cùng session. Result set 1 = các dòng
	 * summary, result set 2 = kWh điện lưới theo giờ (EnergyHour).
	 */
	public MonthlyResult sumMonthly(
			String fac,
			boolean kvh,
			LocalDateTime from,
			LocalDateTime currentTo,
			LocalDateTime prevFrom,
			LocalDateTime prevTo,
			// upper-case box_device_id, never empty
			List<String> excludedMeters
	) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("excludedMeters", excludedMeters)
				.addValue("fac", fac)
				.addValue("isKvh", kvh ? 1 : 0)
				.addValue("from", Timestamp.valueOf(from))
				.addValue("currentTo", Timestamp.valueOf(currentTo))
				.addValue("prevFrom", Timestamp.valueOf(prevFrom))
				.addValue("prevTo", Timestamp.valueOf(prevTo));

		return jdbc.execute(kvh ? KVH_SQL : FAC_SQL, params, (PreparedStatementCallback<MonthlyResult>) ps -> {
			List<RawRow> rows = new ArrayList<>();
			List<EnergyHour> hours = new ArrayList<>();
			int resultIndex = 0;
			boolean isResultSet = ps.execute();

			while (true) {
				if (isResultSet) {
					try (ResultSet rs = ps.getResultSet()) {
						while (rs.next()) {
							if (resultIndex == 0) {
								rows.add(mapRow(rs));
							} else if (resultIndex == 1) {
								hours.add(mapHour(rs));
							}
						}
					}
					resultIndex++;
				} else if (ps.getUpdateCount() == -1) {
					break;
				}

				isResultSet = ps.getMoreResults();
			}

			return new MonthlyResult(List.copyOf(rows), List.copyOf(hours));
		});
	}

	private static EnergyHour mapHour(ResultSet rs) throws SQLException {
		Timestamp day = rs.getTimestamp("energyDay");
		int hour = rs.getInt("hourNumber");

		return new EnergyHour(
				rs.getString("period"),
				day == null ? null : day.toLocalDateTime().toLocalDate().atTime(hour, 0),
				rs.getString("unit"),
				rs.getBigDecimal("kwh")
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

				rs.getBigDecimal("prevValue"),
				rs.getBigDecimal("prevAvgValue"),

				pickAt == null ? null : pickAt.toLocalDateTime(),

				rs.getBigDecimal("solarValue"),
				rs.getBigDecimal("prevSolarValue")
		);
	}
}
