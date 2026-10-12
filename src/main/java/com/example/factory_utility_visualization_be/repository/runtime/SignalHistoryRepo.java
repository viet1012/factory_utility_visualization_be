package com.example.factory_utility_visualization_be.repository.runtime;

import com.example.factory_utility_visualization_be.model.DummyEntity;
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
	// the same hour, keep the latest pick_at.
	@Query(value = """
			SELECT
			    s.plc_address AS plcAddress,
			    s.bucket_at   AS bucketAt,
			    s.[value]     AS value
			FROM (
			    SELECT
			        m.plc_address,
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, m.pick_at), 0) AS bucket_at,
			        m.[value],
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
}
