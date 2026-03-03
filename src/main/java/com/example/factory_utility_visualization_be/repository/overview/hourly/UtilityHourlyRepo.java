package com.example.factory_utility_visualization_be.repository.overview.hourly;


import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UtilityHourlyRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        ;WITH Base AS (
            SELECT
                DATEADD(HOUR, DATEDIFF(HOUR, 0, hi.pick_at), 0) AS hour_time,
                CAST(hi.pick_at AS date) AS record_date,
                hi.[value]
            FROM dbo.F2_Utility_Para_History_Main hi
            JOIN dbo.F2_Utility_Para pa
              ON hi.box_device_id = pa.box_device_id
             AND hi.plc_address  = pa.plc_address
            WHERE hi.pick_at >= DATEADD(HOUR, -:hours, GETDATE())
              AND hi.[value] > 0
              AND pa.name_en = :nameEn
              AND (
                  :fac = 'KVH'
                  OR EXISTS (
                      SELECT 1
                      FROM dbo.F2_Utility_Scada_Channel ch
                      JOIN dbo.F2_Utility_Scada sc ON sc.scada_id = ch.scada_id
                      WHERE ch.box_device_id = hi.box_device_id
                        AND sc.fac = :fac
                  )
              )
        ),
        HourlyData AS (
            SELECT
                DATEPART(HOUR, hour_time) AS HourNumber,
                record_date,
                SUM([value]) AS HourValue
            FROM Base
            GROUP BY DATEPART(HOUR, hour_time), record_date
        )
        SELECT
            HourNumber + 1 AS scaleHour,
            SUM(CASE WHEN record_date = CAST(GETDATE() AS date) THEN HourValue END) AS today,
            SUM(CASE WHEN record_date = DATEADD(DAY,-1, CAST(GETDATE() AS date)) THEN HourValue END) AS yesterday
        FROM HourlyData
        GROUP BY HourNumber
        ORDER BY HourNumber
        """, nativeQuery = true)
	List<Object[]> hourlyCompareRaw(
			@Param("fac") String fac,
			@Param("hours") int hours,
			@Param("nameEn") String nameEn
	);

	default List<HourlyCompareDto> hourlyCompareDto(String fac, int hours, String nameEn) {
		return hourlyCompareRaw(fac, hours, nameEn).stream()
				.map(r -> new HourlyCompareDto(
						((Number) r[0]).intValue(),
						r[1] == null ? null : new java.math.BigDecimal(r[1].toString()),
						r[2] == null ? null : new java.math.BigDecimal(r[2].toString())
				))
				.toList();
	}
}