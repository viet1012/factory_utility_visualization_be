package com.example.factory_utility_visualization_be.repository.overview.monthly;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/*
 * Water / Air của monthly summary (min / max / avg theo kỳ), một SQL cho
 * Fac_A/B/C và KVH.
 *
 * Electricity KHÔNG còn đọc ở đây: kWh, unit và pickAt của dòng Electricity
 * lấy từ HourlyEnergyCache (UtilityMonthlyQueryService). Trước đây batch cũ
 * (#Dev / #Agg) đọc cả ENERGY + SOLAR theo giờ của mọi meter trong
 * [prevFrom, currentTo) chỉ để lấy unit / pickAt, phần lớn số dòng đọc là
 * của điện.
 *
 * Tập thiết bị giữ đúng như batch cũ (FAC_DEVICES / KVH_DEVICES):
 * - Fac_X: param môi trường của thiết bị có channel box_id <> SOLAR thuộc
 *   scada fac X ('%Cooling tank%', 'Data Pipeline pressure',
 *   'Sensor compressed air pressure Data'); FAC_A lấy thêm
 *   'Sensor compressed air pressure Data' của FAC_B.
 * - KVH: 'Cooling tank%' (tiền tố), pipeline, air của thiết bị có channel
 *   box_id <> SOLAR bất kỳ.
 * - Giá trị: Fac cast DECIMAL(18,4), KVH DECIMAL(19,6) (như cũ); value > 0;
 *   kỳ theo pick_at: >= :from -> CURRENT, < :prevTo -> PREV.
 *
 * Trả SUM / COUNT (avg = SUM / COUNT ở Java, như AVG cũ), MIN, MAX, MAX(unit),
 * MAX(pick_at) theo (fac_key, kind, period). :fac = 'ALL' -> mọi fac (tháng
 * đã đóng, cache theo tháng ở MonthlyEnvCache), hoặc một fac key
 * ('KVH' / 'FAC_A' / ...) cho tháng đang chạy.
 *
 * OPTION (RECOMPILE): plan theo đúng khoảng pick_at của lần gọi (tháng đóng
 * nằm trọn trong histogram còn tháng hiện tại thì không -> ước lượng số dòng
 * rất khác nhau, một plan dùng chung cho cả hai là nguyên nhân khả dĩ của
 * chênh lệch 7-10 s / 120 ms).
 */
@Repository
@RequiredArgsConstructor
public class UtilityMonthlyJdbcRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public static final String PERIOD_CURRENT = "CURRENT";
	public static final String PERIOD_PREV = "PREV";

	/**
	 * Một dòng summary đã dựng ở Java (UtilityMonthlyQueryService), giữ
	 * nguyên các cột của SQL cũ.
	 */
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
	 * Water / Air của một (fac, kind, period).
	 *
	 * @param facKey 'KVH' | 'FAC_A' | 'FAC_B' | 'FAC_C' (UPPER(scada fac))
	 * @param kind   'COOLING' | 'PIPELINE' | 'AIR'
	 * @param period 'CURRENT' | 'PREV'
	 */
	public record EnvAgg(
			String facKey,
			String kind,
			String period,
			long count,
			BigDecimal sum,
			BigDecimal min,
			BigDecimal max,
			String unit,
			LocalDateTime maxPickAt
	) {
	}

	public static final String ALL_FACS = "ALL";

	private static final String ENV_SQL = """
          ;WITH EnvPara AS (
              SELECT DISTINCT
                  pa.box_device_id,
                  pa.plc_address,
                  pa.name_en,
                  pa.unit
              FROM dbo.F2_Utility_Para pa
              WHERE pa.name_en LIKE '%Cooling tank%'
                 OR pa.name_en = 'Data Pipeline pressure'
                 OR pa.name_en = 'Sensor compressed air pressure Data'
          ),

          -- devices with a non-SOLAR channel, per SCADA fac
          GridChannel AS (
              SELECT DISTINCT
                  ch.box_device_id,
                  UPPER(sc.fac) AS fac
              FROM dbo.F2_Utility_Scada_Channel ch
              INNER JOIN dbo.F2_Utility_Scada sc
                  ON sc.scada_id = ch.scada_id
              WHERE UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) <> 'SOLAR'
          ),

          DevFac AS (
              -- Fac_X (old FAC_DEVICES)
              SELECT
                  gc.fac AS fac_key,
                  ep.box_device_id,
                  ep.plc_address,
                  ep.unit,
                  CASE
                      WHEN ep.name_en LIKE '%Cooling tank%' THEN 'COOLING'
                      WHEN ep.name_en = 'Data Pipeline pressure' THEN 'PIPELINE'
                      ELSE 'AIR'
                  END AS kind
              FROM EnvPara ep
              INNER JOIN GridChannel gc
                  ON gc.box_device_id = ep.box_device_id

              UNION

              -- FAC_A also shows the compressed air sensors of FAC_B
              SELECT
                  'FAC_A',
                  ep.box_device_id,
                  ep.plc_address,
                  ep.unit,
                  'AIR'
              FROM EnvPara ep
              INNER JOIN GridChannel gc
                  ON gc.box_device_id = ep.box_device_id
              WHERE gc.fac = 'FAC_B'
                AND ep.name_en = 'Sensor compressed air pressure Data'

              UNION

              -- KVH (old KVH_DEVICES): cooling only with the 'Cooling tank' prefix
              SELECT
                  'KVH',
                  ep.box_device_id,
                  ep.plc_address,
                  ep.unit,
                  CASE
                      WHEN ep.name_en LIKE 'Cooling tank%' THEN 'COOLING'
                      WHEN ep.name_en = 'Data Pipeline pressure' THEN 'PIPELINE'
                      ELSE 'AIR'
                  END
              FROM EnvPara ep
              WHERE (
                       ep.name_en LIKE 'Cooling tank%'
                    OR ep.name_en = 'Data Pipeline pressure'
                    OR ep.name_en = 'Sensor compressed air pressure Data'
                )
                AND EXISTS (
                    SELECT 1
                    FROM GridChannel gc
                    WHERE gc.box_device_id = ep.box_device_id
                )
          ),

          Dev AS (
              SELECT d.*
              FROM DevFac d
              WHERE :fac = 'ALL'
                 OR d.fac_key = :fac
          )

          SELECT
              d.fac_key,
              d.kind,
              g.period,
              COUNT_BIG(*)       AS cnt,
              SUM(v.val)         AS sum_value,
              MIN(v.val)         AS min_value,
              MAX(v.val)         AS max_value,
              MAX(d.unit)        AS unit,
              MAX(h.pick_at)     AS max_pick
          FROM Dev d
          INNER JOIN dbo.F2_Utility_Para_History_Main h
              ON h.box_device_id = d.box_device_id
             AND h.plc_address = d.plc_address
          CROSS APPLY (
              SELECT
                  CASE
                      WHEN d.fac_key = 'KVH' THEN CAST(h.[value] AS DECIMAL(19,6))
                      ELSE CAST(CAST(h.[value] AS DECIMAL(18,4)) AS DECIMAL(19,6))
                  END AS val
          ) v
          CROSS APPLY (
              SELECT
                  CASE
                      WHEN h.pick_at >= :from THEN 'CURRENT'
                      WHEN h.pick_at < :prevTo THEN 'PREV'
                  END AS period
          ) g
          WHERE h.pick_at >= :prevFrom
            AND h.pick_at < :currentTo
            AND h.[value] > 0
            AND g.period IS NOT NULL
          GROUP BY
              d.fac_key,
              d.kind,
              g.period
          OPTION (RECOMPILE);
          """;

	/**
	 * @param fac ALL_FACS or one fac key ('KVH', 'FAC_A', ... upper case)
	 */
	public List<EnvAgg> envSummary(
			String fac,
			LocalDateTime from,
			LocalDateTime currentTo,
			LocalDateTime prevFrom,
			LocalDateTime prevTo
	) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("fac", fac)
				.addValue("from", Timestamp.valueOf(from))
				.addValue("currentTo", Timestamp.valueOf(currentTo))
				.addValue("prevFrom", Timestamp.valueOf(prevFrom))
				.addValue("prevTo", Timestamp.valueOf(prevTo));

		return jdbc.query(ENV_SQL, params, (rs, rowNum) -> {
			Timestamp maxPick = rs.getTimestamp("max_pick");

			return new EnvAgg(
					rs.getString("fac_key"),
					rs.getString("kind"),
					rs.getString("period"),
					rs.getLong("cnt"),
					rs.getBigDecimal("sum_value"),
					rs.getBigDecimal("min_value"),
					rs.getBigDecimal("max_value"),
					rs.getString("unit"),
					maxPick == null ? null : maxPick.toLocalDateTime()
			);
		});
	}
}
