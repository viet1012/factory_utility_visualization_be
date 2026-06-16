package com.example.factory_utility_visualization_be.repository.overview.command;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtilityOverviewKpiRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
        SELECT
            COALESCE(SUM(hi.[value]), 0) AS totalValue,
            COALESCE(MAX(pa.unit), 'kWh') AS unit
        FROM dbo.F2_Utility_Para_History_Main hi
        INNER JOIN dbo.F2_Utility_Para pa
            ON hi.box_device_id = pa.box_device_id
           AND hi.plc_address = pa.plc_address
        INNER JOIN dbo.F2_Utility_Scada_Channel ch
            ON hi.box_device_id = ch.box_device_id
        INNER JOIN dbo.F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id
        WHERE hi.[value] > 0
          AND ch.cate = :cate
          AND pa.name_en = :nameEn
          AND hi.pick_at >= :fromDate
          AND hi.pick_at < :toDate
          AND (:factory = 'All Factory' OR sc.fac = :factory)
        """, nativeQuery = true)
	Object[] findTotalUsage(
			@Param("cate") String cate,
			@Param("nameEn") String nameEn,
			@Param("factory") String factory,
			@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate
	);

	@Query(value = """
        SELECT TOP 1
            sc.fac,
            COALESCE(SUM(hi.[value]), 0) AS facValue
        FROM dbo.F2_Utility_Para_History_Main hi
        INNER JOIN dbo.F2_Utility_Para pa
            ON hi.box_device_id = pa.box_device_id
           AND hi.plc_address = pa.plc_address
        INNER JOIN dbo.F2_Utility_Scada_Channel ch
            ON hi.box_device_id = ch.box_device_id
        INNER JOIN dbo.F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id
        WHERE hi.[value] > 0
          AND ch.cate = :cate
          AND pa.name_en = :nameEn
          AND hi.pick_at >= :fromDate
          AND hi.pick_at < :toDate
          AND (:factory = 'All Factory' OR sc.fac = :factory)
        GROUP BY sc.fac
        ORDER BY facValue DESC
        """, nativeQuery = true)
	Object[] findPeakFactory(
			@Param("cate") String cate,
			@Param("nameEn") String nameEn,
			@Param("factory") String factory,
			@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate
	);

	@Query(value = """
        SELECT COUNT(*)
        FROM dbo.F2_Utility_Scada_Channel ch
        INNER JOIN dbo.F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id
        WHERE ch.cate = :cate
          AND (:factory = 'All Factory' OR sc.fac = :factory)
        """, nativeQuery = true)
	Integer countSignals(
			@Param("cate") String cate,
			@Param("factory") String factory
	);

	@Query(value = """
        SELECT COUNT(*)
        FROM dbo.F2_Utility_Scada_Channel ch
        INNER JOIN dbo.F2_Utility_Scada sc
            ON ch.scada_id = sc.scada_id
        OUTER APPLY (
            SELECT TOP 1 hi.pick_at
            FROM dbo.F2_Utility_Para_History_Main hi
            WHERE hi.box_device_id = ch.box_device_id
            ORDER BY hi.pick_at DESC
        ) latest
        WHERE ch.cate = :cate
          AND (:factory = 'All Factory' OR sc.fac = :factory)
          AND (
                latest.pick_at IS NULL
                OR latest.pick_at < :staleTime
          )
        """, nativeQuery = true)
	Integer countStaleOrNoDataSignals(
			@Param("cate") String cate,
			@Param("factory") String factory,
			@Param("staleTime") LocalDateTime staleTime
	);

	@Query(value = """
        ;WITH CleanData AS (
            SELECT *
            FROM dbo.F2_Utility_Para_History_Main
            WHERE pick_at >= :fromDate
              AND pick_at < :toDate
              AND [value] > 0
        ),
        T1 AS (
            SELECT
                sc.fac,
                DATEPART(HOUR, hi.pick_at) AS HourNumber,
                CAST(hi.pick_at AS DATE) AS record_date,
                hi.pick_at,
                hi.[value],
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
            WHERE ch.cate = :cate
              AND pa.name_en = :nameEn
              AND (:factory = 'All Factory' OR sc.fac = :factory)
        ),
        HourlyData AS (
            SELECT
                HourNumber,
                WD,
                CAST(SUM([value]) AS DECIMAL(18, 6)) AS HourValue
            FROM T1
            WHERE [value] IS NOT NULL
            GROUP BY HourNumber, WD
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
                d.HourValue,
                f.vnd_rate
            FROM HourlyData d
            INNER JOIN FinalRate f
                ON d.WD = f.WD
               AND d.HourNumber = f.HourNumber
        )
        SELECT
            COALESCE(SUM(HourValue * vnd_rate), 0) AS totalVnd,
            COALESCE(SUM(HourValue * vnd_rate) / :exchange * :sepzone, 0) AS totalUsd
        FROM CostMapped
        """, nativeQuery = true)
	Object[] findTotalCost(
			@Param("cate") String cate,
			@Param("nameEn") String nameEn,
			@Param("factory") String factory,
			@Param("fromDate") LocalDateTime fromDate,
			@Param("toDate") LocalDateTime toDate,
			@Param("exchange") BigDecimal exchange,
			@Param("sepzone") BigDecimal sepzone
	);
}