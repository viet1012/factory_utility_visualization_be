package com.example.factory_utility_visualization_be.repository.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface UtilityHourlyRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        ;WITH CleanData AS (
            SELECT *
            FROM dbo.F2_Utility_Para_History_Main
            WHERE pick_at > DATEADD(HOUR, -:hours, GETDATE())
              AND [value] > 0
        ),
        T1 AS (
            SELECT
                sc.fac,
                DATEADD(HOUR, DATEDIFF(HOUR, 0, hi.pick_at), 0) AS hour_time,
                CAST(hi.pick_at AS DATE) AS record_date,
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
            WHERE pa.name_en = :nameEn
              AND (:fac = 'KVH' OR sc.fac = :fac)
        ),
        HourlyData AS (
            SELECT
                HourNumber,
                WD,
                record_date,
                SUM([value]) AS HourValue
            FROM T1
            WHERE [value] IS NOT NULL
            GROUP BY HourNumber, WD, record_date
        ),
        Hours AS (
            SELECT 0 AS n UNION ALL SELECT 1  UNION ALL SELECT 2  UNION ALL
            SELECT 3      UNION ALL SELECT 4  UNION ALL SELECT 5  UNION ALL
            SELECT 6      UNION ALL SELECT 7  UNION ALL SELECT 8  UNION ALL
            SELECT 9      UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL
            SELECT 12     UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL
            SELECT 15     UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL
            SELECT 18     UNION ALL SELECT 19 UNION ALL SELECT 20 UNION ALL
            SELECT 21     UNION ALL SELECT 22 UNION ALL SELECT 23
        ),
        HourCost AS (
            SELECT
                c.WD,
                h.n AS HourNumber,
                SUM(
                    CASE
                        WHEN c.frTime < c.toTime THEN
                            (CASE WHEN c.toTime < h.n + 1 THEN c.toTime ELSE h.n + 1 END)
                          - (CASE WHEN c.frTime > h.n     THEN c.frTime ELSE h.n     END)
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
                          - (CASE WHEN c.frTime > h.n     THEN c.frTime ELSE h.n     END)
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
        CostMapped AS (
            SELECT
                d.HourNumber,
                d.WD,
                d.record_date,
                d.HourValue,
                f.vnd_rate
            FROM HourlyData d
            INNER JOIN FinalRate f
                ON d.WD = f.WD
               AND d.HourNumber = f.HourNumber
        )
        SELECT
            HourNumber AS scaleHour,
            SUM(CASE
                    WHEN record_date = DATEADD(DAY, -1, CAST(GETDATE() AS DATE))
                    THEN HourValue
                END) AS yesterday,
            SUM(CASE
                    WHEN record_date = CAST(GETDATE() AS DATE)
                    THEN HourValue
                END) AS today,
            SUM(CASE
                    WHEN record_date = DATEADD(DAY, -1, CAST(GETDATE() AS DATE))
                    THEN HourValue * vnd_rate
                END) AS yesterdayVnd,
            SUM(CASE
                    WHEN record_date = CAST(GETDATE() AS DATE)
                    THEN HourValue * vnd_rate
                END) AS todayVnd,
            SUM(CASE
                    WHEN record_date = DATEADD(DAY, -1, CAST(GETDATE() AS DATE))
                    THEN HourValue * vnd_rate
                END) / :exchange * :sepzone AS yesterdayUsd,
            SUM(CASE
                    WHEN record_date = CAST(GETDATE() AS DATE)
                    THEN HourValue * vnd_rate
                END) / :exchange * :sepzone AS todayUsd
        FROM CostMapped
        GROUP BY HourNumber
        ORDER BY HourNumber
        """, nativeQuery = true)
	List<Object[]> hourlyCompareRaw(
			@Param("fac") String fac,
			@Param("hours") int hours,
			@Param("nameEn") String nameEn,
			@Param("exchange") BigDecimal exchange,
			@Param("sepzone") BigDecimal sepzone
	);

	default List<HourlyCompareDto> hourlyCompareDto(
			String fac,
			int hours,
			String nameEn,
			BigDecimal exchange,
			BigDecimal sepzone
	) {
		return hourlyCompareRaw(fac, hours, nameEn, exchange, sepzone).stream()
				.map(r -> new HourlyCompareDto(
						((Number) r[0]).intValue(),
						r[1] == null ? null : new BigDecimal(r[1].toString()), // yesterday
						r[2] == null ? null : new BigDecimal(r[2].toString()), // today
						r[5] == null ? null : new BigDecimal(r[5].toString()), // yesterdayUsd
						r[6] == null ? null : new BigDecimal(r[6].toString())  // todayUsd
				))
				.toList();
	}
}