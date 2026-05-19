package com.example.factory_utility_visualization_be.repository.overview.monthly;


import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyUtilityUsageDto;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public interface MonthlyUtilityUsageRepo extends JpaRepository<DummyEntity, Long> {

	private static String str(Object value) {
		return value == null ? "" : value.toString();
	}

	private static BigDecimal decimal(Object value) {
		if (value == null) return BigDecimal.ZERO;
		if (value instanceof BigDecimal bd) return bd;
		return new BigDecimal(value.toString());
	}

	private static LocalDateTime time(Object value) {
		if (value == null) return null;
		if (value instanceof Timestamp ts) return ts.toLocalDateTime();
		if (value instanceof LocalDateTime dt) return dt;
		return Timestamp.valueOf(value.toString()).toLocalDateTime();
	}

	@Query(value = """
			SELECT
			    sc.fac AS fac,
			    ch.box_id AS boxId,
			    MAX(hi.box_device_id) AS boxDeviceId,
			    pa.name_en AS name,
			    ch.cate AS cate,
			    pa.unit AS unit,
			    CAST(MAX(hi.[value]) - MIN(hi.[value]) AS DECIMAL(18, 2)) AS usedValue,
			    MIN(hi.pick_at) AS firstPickAt,
			    MAX(hi.pick_at) AS lastPickAt,
			    CAST(MIN(hi.[value]) AS DECIMAL(18, 2)) AS firstValue,
			    CAST(MAX(hi.[value]) AS DECIMAL(18, 2)) AS lastValue
			FROM F2_Utility_Para_History_Main hi
			INNER JOIN F2_Utility_Para pa
			    ON hi.box_device_id = pa.box_device_id
			   AND hi.plc_address = pa.plc_address
			INNER JOIN F2_Utility_Scada_Channel ch
			    ON hi.box_device_id = ch.box_device_id
			INNER JOIN F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id
			WHERE hi.[value] > 0
			  AND pa.name_en = :nameEn
			  AND hi.pick_at >= :fromDate
			  AND hi.pick_at < :toDate
			  AND (:fac = 'KVH' OR sc.fac = :fac)
			GROUP BY
			    sc.fac,
			    ch.box_id,
			    pa.name_en,
			    ch.cate,
			    pa.unit
			HAVING MAX(hi.[value]) - MIN(hi.[value]) > 0
			ORDER BY usedValue DESC
			""", nativeQuery = true)
	List<Object[]> findMonthlyUtilityUsageRaw(
			@Param("fac") String fac,
			@Param("nameEn") String nameEn,
			@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate
	);

	default List<MonthlyUtilityUsageDto> findMonthlyUtilityUsageDto(
			String fac,
			String nameEn,
			LocalDateTime fromDate,
			LocalDateTime toDate
	) {
		return findMonthlyUtilityUsageRaw(fac, nameEn, fromDate, toDate)
				.stream()
				.map(r -> new MonthlyUtilityUsageDto(
						str(r[0]),
						str(r[1]),
						str(r[2]),
						str(r[3]),
						str(r[4]),
						str(r[5]),
						decimal(r[6]),
						time(r[7]),
						time(r[8]),
						decimal(r[9]),
						decimal(r[10])
				))
				.sorted(
						(a, b) -> b.usedValue().compareTo(a.usedValue())
				)
				.toList();
	}
}