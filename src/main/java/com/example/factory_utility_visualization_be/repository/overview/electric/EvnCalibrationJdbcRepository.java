package com.example.factory_utility_visualization_be.repository.overview.electric;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * F2_Utility_Evn_Daily (official EVN kWh from the F2 sheet) and
 * F2_Utility_Loss_Calibration (append-only loss per hour), see
 * docs/sql/f2_utility_evn_daily_and_loss_calibration.sql.
 * Never reads or writes History_Main.
 */
@Repository
@RequiredArgsConstructor
public class EvnCalibrationJdbcRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public record EvnDay(
			LocalDate day,
			// KVH | FAC_AB | FAC_C
			String scope,
			BigDecimal normalKwh,
			BigDecimal highKwh,
			BigDecimal lowKwh,
			BigDecimal totalKwh,
			// KVH only, else null
			BigDecimal solarKwh,
			String sourceFile,
			LocalDateTime importedAt,
			String importedBy
	) {
		// same kWh values (source / audit columns ignored)
		public boolean sameValues(EvnDay other) {
			return eq(normalKwh, other.normalKwh) && eq(highKwh, other.highKwh) && eq(lowKwh, other.lowKwh)
					&& eq(totalKwh, other.totalKwh) && eq(solarKwh, other.solarKwh);
		}

		private static boolean eq(BigDecimal a, BigDecimal b) {
			return a == null ? b == null : b != null && a.compareTo(b) == 0;
		}
	}

	public record LossCalibration(
			long id,
			String scope,
			BigDecimal lossKwhPerHour,
			LocalDate effectiveFrom,
			// inclusive, null = open ended
			LocalDate effectiveTo,
			String method,
			LocalDateTime createdAt,
			String createdBy
	) {
		public boolean covers(LocalDate day) {
			return !day.isBefore(effectiveFrom) && (effectiveTo == null || !day.isAfter(effectiveTo));
		}
	}

	// ---------------------------------------------------------------------
	// F2_Utility_Evn_Daily
	// ---------------------------------------------------------------------

	/** Rows of [from, to); scope null = every scope. */
	public List<EvnDay> findEvnDays(LocalDate from, LocalDate to, String scope) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("from", Date.valueOf(from))
				.addValue("to", Date.valueOf(to))
				.addValue("scope", scope, Types.VARCHAR);

		return jdbc.query("""
				SELECT day, scope, normal_kwh, high_kwh, low_kwh, total_kwh, solar_kwh,
				       source_file, imported_at, imported_by
				FROM dbo.F2_Utility_Evn_Daily
				WHERE day >= :from AND day < :to
				  AND (:scope IS NULL OR scope = :scope)
				ORDER BY day, scope
				""", params, (rs, i) -> evnDay(rs));
	}

	private static final String INSERT_EVN_DAY = """
			INSERT INTO dbo.F2_Utility_Evn_Daily
			    (day, scope, normal_kwh, high_kwh, low_kwh, total_kwh, solar_kwh,
			     source_file, imported_at, imported_by)
			VALUES (:day, :scope, :normal, :high, :low, :total, :solar,
			        :sourceFile, :importedAt, :importedBy)
			""";

	private static final String UPDATE_EVN_DAY = """
			UPDATE dbo.F2_Utility_Evn_Daily
			SET normal_kwh = :normal, high_kwh = :high, low_kwh = :low, total_kwh = :total,
			    solar_kwh = :solar, source_file = :sourceFile, imported_at = :importedAt,
			    imported_by = :importedBy
			WHERE day = :day AND scope = :scope
			""";

	// JDBC batch size (a whole workbook is ~4,500 rows)
	private static final int BATCH = 500;

	public void insertEvnDay(EvnDay row) {
		jdbc.update(INSERT_EVN_DAY, evnParams(row));
	}

	public void updateEvnDay(EvnDay row) {
		jdbc.update(UPDATE_EVN_DAY, evnParams(row));
	}

	public void insertEvnDays(List<EvnDay> rows) {
		batch(INSERT_EVN_DAY, rows);
	}

	public void updateEvnDays(List<EvnDay> rows) {
		batch(UPDATE_EVN_DAY, rows);
	}

	private void batch(String sql, List<EvnDay> rows) {
		for (int i = 0; i < rows.size(); i += BATCH) {
			jdbc.batchUpdate(sql, rows.subList(i, Math.min(i + BATCH, rows.size())).stream()
					.map(EvnCalibrationJdbcRepository::evnParams)
					.toArray(MapSqlParameterSource[]::new));
		}
	}

	private static MapSqlParameterSource evnParams(EvnDay row) {
		return new MapSqlParameterSource()
				.addValue("day", Date.valueOf(row.day()))
				.addValue("scope", row.scope())
				.addValue("normal", row.normalKwh())
				.addValue("high", row.highKwh())
				.addValue("low", row.lowKwh())
				.addValue("total", row.totalKwh())
				.addValue("solar", row.solarKwh())
				.addValue("sourceFile", row.sourceFile())
				.addValue("importedAt", Timestamp.valueOf(row.importedAt()))
				.addValue("importedBy", row.importedBy());
	}

	private static EvnDay evnDay(ResultSet rs) throws SQLException {
		Timestamp importedAt = rs.getTimestamp("imported_at");

		return new EvnDay(
				rs.getDate("day").toLocalDate(),
				rs.getString("scope").trim(),
				rs.getBigDecimal("normal_kwh"),
				rs.getBigDecimal("high_kwh"),
				rs.getBigDecimal("low_kwh"),
				rs.getBigDecimal("total_kwh"),
				rs.getBigDecimal("solar_kwh"),
				rs.getString("source_file"),
				importedAt == null ? null : importedAt.toLocalDateTime(),
				rs.getString("imported_by"));
	}

	// ---------------------------------------------------------------------
	// F2_Utility_Loss_Calibration
	// ---------------------------------------------------------------------

	/** Every calibration row (small table), newest id first. */
	public List<LossCalibration> findCalibrations() {
		return jdbc.query("""
				SELECT id, scope, loss_kwh_per_hour, effective_from, effective_to, method,
				       created_at, created_by
				FROM dbo.F2_Utility_Loss_Calibration
				ORDER BY id DESC
				""", new MapSqlParameterSource(), (rs, i) -> {
			Date to = rs.getDate("effective_to");
			Timestamp createdAt = rs.getTimestamp("created_at");

			return new LossCalibration(
					rs.getLong("id"),
					rs.getString("scope").trim(),
					rs.getBigDecimal("loss_kwh_per_hour"),
					rs.getDate("effective_from").toLocalDate(),
					to == null ? null : to.toLocalDate(),
					rs.getString("method"),
					createdAt == null ? null : createdAt.toLocalDateTime(),
					rs.getString("created_by"));
		});
	}

	/** Appends a row (old rows are never changed); returns the new id. */
	public long insertCalibration(
			String scope,
			BigDecimal lossKwhPerHour,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			String method,
			LocalDateTime createdAt,
			String createdBy
	) {
		KeyHolder keys = new GeneratedKeyHolder();

		jdbc.update("""
				INSERT INTO dbo.F2_Utility_Loss_Calibration
				    (scope, loss_kwh_per_hour, effective_from, effective_to, method, created_at, created_by)
				VALUES (:scope, :loss, :from, :to, :method, :createdAt, :createdBy)
				""", new MapSqlParameterSource()
				.addValue("scope", scope)
				.addValue("loss", lossKwhPerHour)
				.addValue("from", Date.valueOf(effectiveFrom))
				.addValue("to", effectiveTo == null ? null : Date.valueOf(effectiveTo))
				.addValue("method", method)
				.addValue("createdAt", Timestamp.valueOf(createdAt))
				.addValue("createdBy", createdBy), keys, new String[] {"id"});

		Number id = keys.getKey();
		return id == null ? -1 : id.longValue();
	}
}
