package com.example.factory_utility_visualization_be.repository.runtime;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryDailyBucketProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryMetaProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryOldestRawProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryRawBucketProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistorySnapshotProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Queries for GET /api/utility/series/history.
 * Filters are always concrete (no "(:x IS NULL OR ...)") so SQL Server can
 * seek on (box_device_id, plc_address, recorded_at). No scada_channel join.
 */
public interface SignalHistoryRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			SELECT
			    p.plc_address AS plcAddress,
			    p.name_en     AS nameEn,
			    p.unit        AS unit,
			    p.cate_id     AS cateId
			FROM dbo.F2_Utility_Para p
			WHERE p.box_device_id = :boxDeviceId
			ORDER BY p.plc_address, p.id
			""", nativeQuery = true)
	List<SignalHistoryMetaProjection> findSignalMeta(
			@Param("boxDeviceId") String boxDeviceId
	);

	@Query(value = """
			SELECT
			    p.plc_address AS plcAddress,
			    p.name_en     AS nameEn,
			    p.unit        AS unit,
			    p.cate_id     AS cateId
			FROM dbo.F2_Utility_Para p
			WHERE p.box_device_id = :boxDeviceId
			  AND p.plc_address IN (:plcAddresses)
			ORDER BY p.plc_address, p.id
			""", nativeQuery = true)
	List<SignalHistoryMetaProjection> findSignalMetaByPlc(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddresses") List<String> plcAddresses
	);

	// PK seek on id: the oldest row is the earliest raw sample still kept.
	@Query(value = """
			SELECT TOP (1) h.recorded_at AS recordedAt
			FROM dbo.F2_Utility_Para_History h
			ORDER BY h.id ASC
			""", nativeQuery = true)
	Optional<SignalHistoryOldestRawProjection> findOldestRawRecordedAt();

	// Bucket expression is computed once in the innermost derived table: a
	// named parameter repeated in SELECT and GROUP BY is bound as two
	// different JDBC parameters, and SQL Server would reject the GROUP BY.
	// lastValue = value of the latest sample in the bucket (rn = 1); only
	// columns of the (box_device_id, plc_address, recorded_at) INCLUDE value
	// index are read.
	@Query(value = """
			SELECT
			    r.plc_address          AS plcAddress,
			    r.bucket_at            AS bucketAt,
			    MIN(r.[value])         AS minValue,
			    AVG(r.[value])         AS avgValue,
			    MAX(r.[value])         AS maxValue,
			    COUNT_BIG(r.[value])   AS sampleCount,
			    MAX(CASE WHEN r.rn = 1 THEN r.[value] END) AS lastValue
			FROM (
			    SELECT
			        b.box_device_id,
			        b.plc_address,
			        b.bucket_at,
			        b.[value],
			        ROW_NUMBER() OVER (
			            PARTITION BY b.plc_address, b.bucket_at
			            ORDER BY b.recorded_at DESC
			        ) AS rn
			    FROM (
			        SELECT
			            h.box_device_id,
			            h.plc_address,
			            h.recorded_at,
			            DATEADD(
			                MINUTE,
			                (DATEDIFF(MINUTE, 0, h.recorded_at) / :bucketMinutes) * :bucketMinutes,
			                0
			            ) AS bucket_at,
			            h.[value]
			        FROM dbo.F2_Utility_Para_History h
			        WHERE h.box_device_id = :boxDeviceId
			          AND h.plc_address IN (:plcAddresses)
			          AND h.recorded_at >= :fromTs
			          AND h.recorded_at <  :toTs
			    ) b
			) r
			GROUP BY r.box_device_id, r.plc_address, r.bucket_at
			ORDER BY r.plc_address, r.bucket_at
			""", nativeQuery = true)
	List<SignalHistoryRawBucketProjection> findRawBuckets(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddresses") List<String> plcAddresses,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			@Param("bucketMinutes") int bucketMinutes
	);

	// One snapshot per hour; if the collector wrote more than one row for
	// the same hour, keep the latest pick_at. bucketAt = pick_at hour (the
	// service moves COUNTER rows back one hour).
	// value       = reading at pick_at: cur_value, value when cur_value is NULL
	// consumption = raw value (COUNTER: cur_value - prev_value of the hour
	//               ending at pick_at)
	@Query(value = """
			SELECT
			    s.plc_address AS plcAddress,
			    s.bucket_at   AS bucketAt,
			    s.reading     AS value,
			    s.consumption AS consumption
			FROM (
			    SELECT
			        m.plc_address,
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, m.pick_at), 0) AS bucket_at,
			        COALESCE(m.cur_value, m.[value])               AS reading,
			        m.[value]                                      AS consumption,
			        ROW_NUMBER() OVER (
			            PARTITION BY m.plc_address, DATEADD(HOUR, DATEDIFF(HOUR, 0, m.pick_at), 0)
			            ORDER BY m.pick_at DESC
			        ) AS rn
			    FROM dbo.F2_Utility_Para_History_Main m
			    WHERE m.box_device_id = :boxDeviceId
			      AND m.plc_address IN (:plcAddresses)
			      AND m.pick_at >= :fromTs
			      AND m.pick_at <  :toTs
			) s
			WHERE s.rn = 1
			ORDER BY s.plc_address, s.bucket_at
			""", nativeQuery = true)
	List<SignalHistorySnapshotProjection> findHourlySnapshots(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddresses") List<String> plcAddresses,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs
	);

	// bucket=1d. day = CAST(pick_at + :shiftHours AS DATE): 0 for ANALOG,
	// -1 for COUNTER (a row holds the hour ending at pick_at, so pick_at
	// 00:00 belongs to the previous day); fromTs/toTs are shifted the same
	// way by the caller. Readings use cur_value (value when NULL), cast to
	// DECIMAL(19,4) before aggregating; last = latest pick_at of the day;
	// positiveSum / positiveCount = SUM / COUNT of raw value > 0 (COUNTER
	// daily consumption). The day is computed once in the innermost derived
	// table so :shiftHours is bound only once. No F2_Utility_Para join and
	// no LTRIM/RTRIM on the box filter, so the index seek is kept.
	@Query(value = """
			SELECT
			    d.plc_address                                        AS plcAddress,
			    CAST(d.record_date AS DATETIME)                      AS bucketAt,
			    CAST(MIN(d.v) AS DECIMAL(19, 4))                     AS minValue,
			    CAST(AVG(d.v) AS DECIMAL(19, 4))                     AS avgValue,
			    CAST(MAX(d.v) AS DECIMAL(19, 4))                     AS maxValue,
			    CAST(MAX(CASE WHEN d.rn_last = 1 THEN d.v END) AS DECIMAL(19, 4)) AS lastValue,
			    COUNT_BIG(*)                                         AS sampleCount,
			    CAST(SUM(CASE WHEN d.consumption > 0 THEN d.consumption ELSE 0 END) AS DECIMAL(19, 4)) AS positiveSum,
			    COUNT_BIG(CASE WHEN d.consumption > 0 THEN 1 END)    AS positiveCount
			FROM (
			    SELECT
			        r.plc_address,
			        r.record_date,
			        r.v,
			        r.consumption,
			        ROW_NUMBER() OVER (
			            PARTITION BY r.plc_address, r.record_date
			            ORDER BY r.pick_at DESC
			        ) AS rn_last
			    FROM (
			        SELECT
			            m.plc_address,
			            m.pick_at,
			            CAST(DATEADD(HOUR, :shiftHours, m.pick_at) AS DATE)   AS record_date,
			            CAST(COALESCE(m.cur_value, m.[value]) AS DECIMAL(19, 4)) AS v,
			            CAST(m.[value] AS DECIMAL(19, 4))                    AS consumption
			        FROM dbo.F2_Utility_Para_History_Main m
			        WHERE m.box_device_id = :boxDeviceId
			          AND m.plc_address IN (:plcAddresses)
			          AND m.pick_at >= :fromTs
			          AND m.pick_at <  :toTs
			          AND COALESCE(m.cur_value, m.[value]) IS NOT NULL
			    ) r
			) d
			GROUP BY d.plc_address, d.record_date
			ORDER BY d.plc_address, d.record_date
			""", nativeQuery = true)
	List<SignalHistoryDailyBucketProjection> findDailyBuckets(
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddresses") List<String> plcAddresses,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			@Param("shiftHours") int shiftHours
	);
}
