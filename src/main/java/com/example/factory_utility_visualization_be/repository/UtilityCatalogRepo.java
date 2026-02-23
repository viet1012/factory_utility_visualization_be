package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.dto.ChannelDto;
import com.example.factory_utility_visualization_be.dto.ParamDto;
import com.example.factory_utility_visualization_be.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UtilityCatalogRepo {

	private final JdbcTemplate jdbc;

	// =========================================================
	// 1) SCADAS
	// =========================================================
	public List<ScadaDto> findScadas(String fac) {

		String sql = """
				    SELECT 
				        scada_id   AS scadaId,
				        fac        AS fac,
				        plc_ip     AS plcIp,
				        plc_port   AS plcPort,
				        COALESCE(wlan, '') AS wlan
				    FROM f2_utility_scada
				    WHERE (? IS NULL OR fac = ?)
				    ORDER BY fac, scada_id
				""";

		return jdbc.query(sql, (rs, i) -> mapScada(rs), fac, fac);
	}


	// =========================================================
	// 2) CHANNELS  (JOIN SCADA để filter fac)
	// =========================================================
	public List<ChannelDto> findChannels(String fac,
	                                     String scadaId,
	                                     String cate) {

		String sql = """
				    SELECT
				        c.id,
				        c.scada_id      AS scadaId,
				        c.cate,
				        c.box_device_id AS boxDeviceId,
				        c.box_id        AS boxId
				    FROM f2_utility_scada_channel c
				    JOIN f2_utility_scada s
				      ON c.scada_id = s.scada_id
				    WHERE (? IS NULL OR s.fac = ?)
				      AND (? IS NULL OR c.scada_id = ?)
				      AND (? IS NULL OR c.cate = ?)
				    ORDER BY s.fac, c.scada_id, c.cate, c.box_device_id
				""";

		return jdbc.query(sql, (rs, i) -> mapChannel(rs),
				fac, fac,
				scadaId, scadaId,
				cate, cate
		);
	}


	// =========================================================
	// 3) PARAMS  (JOIN CHANNEL + SCADA để filter fac)
	// =========================================================
	public List<ParamDto> findParams(String fac,
	                                 String scadaId,
	                                 String cate,
	                                 String boxDeviceId,
	                                 Boolean importantOnly) {

		String sql = """
				SELECT
				           p.id,
				           p.box_device_id AS boxDeviceId,
				           p.plc_address   AS plcAddress,
				           p.value_type    AS valueType,
				           p.unit,
				           p.name_vi       AS nameVi,
				           p.name_en       AS nameEn,
				           p.is_important  AS isImportant,
				           p.is_alert      AS isAlert,
				           p.cate_id       AS cateId,
				
				           mp.cate_name    AS cateName,
				           mp.pick_hour    AS pickHour,
				
				           c.scada_id      AS scadaId,
				           s.fac           AS fac,
				           c.cate          AS cate,
				           c.box_id        AS boxId
				       FROM f2_utility_para p
				       JOIN f2_utility_scada_channel c ON p.box_device_id = c.box_device_id
				       JOIN f2_utility_scada s         ON c.scada_id = s.scada_id
				       LEFT JOIN F2_Utility_Master_PickData mp ON mp.cate_id = p.cate_id
				       WHERE (? IS NULL OR s.fac = ?)
				         AND (? IS NULL OR c.scada_id = ?)
				         AND (? IS NULL OR c.cate = ?)
				         AND (? IS NULL OR p.box_device_id = ?)
				         AND (? IS NULL OR p.is_important = ?)
				       ORDER BY s.fac, c.scada_id, c.cate, p.box_device_id, p.plc_address;
				
				""";

		return jdbc.query(sql, (rs, i) -> mapParam(rs),
				fac, fac,
				scadaId, scadaId,
				cate, cate,
				boxDeviceId, boxDeviceId,
				importantOnly, importantOnly
		);
	}


	// =========================================================
	// 4) LATEST  (JOIN đầy đủ để filter fac/scada/cate)
	// =========================================================
	public List<LatestRecordDto> findLatest(String fac,
	                                        String scadaId,
	                                        String cate,
	                                        String boxDeviceId) {

		String sql = """
				    WITH latest AS (
				        SELECT box_device_id, plc_address, MAX(recorded_at) AS max_time
				        FROM f2_utility_para_history
				        GROUP BY box_device_id, plc_address
				    )
				    SELECT
				        h.box_device_id AS boxDeviceId,
				        h.plc_address   AS plcAddress,
				        h.value,
				        h.recorded_at   AS recordedAt
				    FROM f2_utility_para_history h
				    JOIN latest l
				      ON h.box_device_id = l.box_device_id
				     AND h.plc_address   = l.plc_address
				     AND h.recorded_at   = l.max_time
				    JOIN f2_utility_scada_channel c
				      ON h.box_device_id = c.box_device_id
				    JOIN f2_utility_scada s
				      ON c.scada_id = s.scada_id
				    WHERE (? IS NULL OR s.fac = ?)
				      AND (? IS NULL OR c.scada_id = ?)
				      AND (? IS NULL OR c.cate = ?)
				      AND (? IS NULL OR h.box_device_id = ?)
				    ORDER BY s.fac, c.scada_id, h.box_device_id, h.plc_address
				""";

		return jdbc.query(sql, (rs, i) -> mapLatest(rs),
				fac, fac,
				scadaId, scadaId,
				cate, cate,
				boxDeviceId, boxDeviceId
		);
	}


	// =========================================================
	// ======================= MAPPERS =========================
	// =========================================================

	private ScadaDto mapScada(ResultSet rs) throws java.sql.SQLException {
		ScadaDto d = new ScadaDto();
		d.setScadaId(rs.getString("scadaId"));
		d.setFac(rs.getString("fac"));
		d.setPlcIp(rs.getString("plcIp"));
		d.setPlcPort((Integer) rs.getObject("plcPort"));
		d.setWlan(rs.getString("wlan"));
		return d;
	}

	private ChannelDto mapChannel(ResultSet rs) throws java.sql.SQLException {
		ChannelDto d = new ChannelDto();
		d.setId(rs.getLong("id"));
		d.setScadaId(rs.getString("scadaId"));
		d.setCate(rs.getString("cate"));
		d.setBoxDeviceId(rs.getString("boxDeviceId"));
		d.setBoxId(rs.getString("boxId"));
		return d;
	}

	private ParamDto mapParam(ResultSet rs) throws java.sql.SQLException {
		ParamDto d = new ParamDto();
		d.setId(rs.getLong("id"));
		d.setBoxDeviceId(rs.getString("boxDeviceId"));
		d.setPlcAddress(rs.getString("plcAddress"));
		d.setValueType(rs.getString("valueType"));
		d.setUnit(rs.getString("unit"));

		d.setNameVi(rs.getString("nameVi"));
		d.setNameEn(rs.getString("nameEn"));

		d.setIsImportant(toBool(rs.getObject("isImportant")));
		d.setIsAlert(toBool(rs.getObject("isAlert")));

		d.setCateId(rs.getString("cateId"));
		d.setCateName(rs.getString("cateName"));
		d.setPickHour((BigDecimal) rs.getObject("pickHour"));

		d.setScadaId(rs.getString("scadaId"));
		d.setFac(rs.getString("fac"));
		d.setCate(rs.getString("cate"));
		d.setBoxId(rs.getString("boxId"));
		return d;
	}

	private Boolean toBool(Object v) {
		if (v == null) return null;
		if (v instanceof Boolean b) return b;
		if (v instanceof Number n) return n.intValue() != 0;
		if (v instanceof String s) {
			s = s.trim();
			if (s.isEmpty()) return null;
			return s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("y");
		}
		return "1".equals(v.toString()) || "true".equalsIgnoreCase(v.toString());
	}


	private LatestRecordDto mapLatest(ResultSet rs) throws java.sql.SQLException {
		LatestRecordDto d = new LatestRecordDto();
		d.setBoxDeviceId(rs.getString("boxDeviceId"));
		d.setPlcAddress(rs.getString("plcAddress"));
		d.setValue(rs.getBigDecimal("value"));
		d.setRecordedAt(
				rs.getTimestamp("recordedAt") == null
						? null
						: rs.getTimestamp("recordedAt").toLocalDateTime()
		);
		return d;
	}
}
