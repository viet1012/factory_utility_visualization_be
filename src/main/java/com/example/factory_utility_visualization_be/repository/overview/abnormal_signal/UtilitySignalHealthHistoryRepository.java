package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalHistoricalWindowStatsProjection;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalHealthHistoryProjection;

public interface UtilitySignalHealthHistoryRepository extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			WITH hourly_buckets AS (
			    SELECT CASE
			               WHEN :from = DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			                   THEN DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			               ELSE DATEADD(HOUR, DATEDIFF(HOUR, 0, :from) + 1, 0)
			           END AS bucket_at
			    WHERE :from < :to
			      AND CASE
			              WHEN :from = DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			                  THEN DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			              ELSE DATEADD(HOUR, DATEDIFF(HOUR, 0, :from) + 1, 0)
			          END < :to

			    UNION ALL

			    SELECT DATEADD(HOUR, 1, bucket_at)
			    FROM hourly_buckets
			    WHERE DATEADD(HOUR, 1, bucket_at) < :to
			),
			active_parameter_codes AS (
			    SELECT DISTINCT UPPER(LTRIM(RTRIM(alert_rule.ParameterCode))) AS parameter_code
			    FROM dbo.F2_Utility_Alert_Master alert_rule
			    WHERE alert_rule.IsActive = 1
			      AND alert_rule.ParameterCode IS NOT NULL
			),
			eligible_signals AS (
			    SELECT DISTINCT sc.fac,
			           sc.scada_id,
			           channel.cate,
			           para.id AS para_id,
			           para.cate_id AS parameter_code,
			           para.name_en AS signal_name,
			           para.unit,
			           para.box_device_id,
			           para.plc_address
			    FROM dbo.F2_Utility_Para para
			    INNER JOIN dbo.F2_Utility_Scada_Channel channel
			            ON channel.box_device_id = para.box_device_id
			    INNER JOIN dbo.F2_Utility_Scada sc
			            ON sc.scada_id = channel.scada_id
			    INNER JOIN active_parameter_codes active_rule
			            ON active_rule.parameter_code = UPPER(LTRIM(RTRIM(para.cate_id)))
			    WHERE (para.name_en IS NULL OR para.name_en NOT LIKE 'Slave%')
			      AND (:fac IS NULL OR sc.fac = :fac)
			      AND (:cate IS NULL OR channel.cate = :cate)
			      AND (:scadaId IS NULL OR sc.scada_id = :scadaId)
			      AND (:boxDeviceId IS NULL OR para.box_device_id = :boxDeviceId)
			),
			eligible_signal_keys AS (
			    SELECT DISTINCT box_device_id, plc_address
			    FROM eligible_signals
			),
			deduplicated AS (
			    SELECT history.box_device_id,
			           history.plc_address,
			           history.pick_at,
			           history.cur_value,
			           history.cur_recorded_at,
			           history.prev_value,
			           history.prev_recorded_at,
			           ROW_NUMBER() OVER (
			               PARTITION BY history.box_device_id, history.plc_address, history.pick_at
			               ORDER BY history.id DESC
			           ) AS rn
			    FROM dbo.F2_Utility_Para_History_Main history
			    INNER JOIN eligible_signal_keys signal_key
			            ON signal_key.box_device_id = history.box_device_id
			           AND signal_key.plc_address = history.plc_address
			    WHERE history.pick_at >= :from
			      AND history.pick_at < :to
			)
			SELECT sc.fac AS fac,
			       sc.scada_id AS scadaId,
			       sc.cate AS cate,
			       sc.para_id AS paraId,
			       sc.parameter_code AS parameterCode,
			       sc.signal_name AS signalName,
			       sc.unit AS unit,
			       sc.box_device_id AS boxDeviceId,
			       sc.plc_address AS plcAddress,
			       bucket.bucket_at AS pickAt,
			       snapshot.cur_value AS currentValue,
			       snapshot.cur_recorded_at AS currentRecordedAt,
			       snapshot.prev_value AS prevValue,
			       snapshot.prev_recorded_at AS prevRecordedAt
			FROM hourly_buckets bucket
			LEFT JOIN eligible_signals sc ON 1 = 1
			LEFT JOIN deduplicated snapshot
			       ON snapshot.box_device_id = sc.box_device_id
			      AND snapshot.plc_address = sc.plc_address
			      AND snapshot.pick_at = bucket.bucket_at
			      AND snapshot.rn = 1
			ORDER BY bucket.bucket_at,
			         sc.fac,
			         sc.cate,
			         sc.scada_id,
			         sc.box_device_id,
			         sc.plc_address
			OPTION (MAXRECURSION 32767)
			""", nativeQuery = true)
	List<UtilitySignalHealthHistoryProjection> findExpectedHourlySnapshots(
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("fac") String fac,
			@Param("cate") String cate,
			@Param("scadaId") String scadaId,
			@Param("boxDeviceId") String boxDeviceId
	);

	@Query(value = """
			WITH hourly_buckets AS (
			    SELECT CASE
			               WHEN :from = DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			                   THEN DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			               ELSE DATEADD(HOUR, DATEDIFF(HOUR, 0, :from) + 1, 0)
			           END AS bucket_at
			    WHERE :from < :to
			      AND CASE
			              WHEN :from = DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			                  THEN DATEADD(HOUR, DATEDIFF(HOUR, 0, :from), 0)
			              ELSE DATEADD(HOUR, DATEDIFF(HOUR, 0, :from) + 1, 0)
			          END < :to

			    UNION ALL

			    SELECT DATEADD(HOUR, 1, bucket_at)
			    FROM hourly_buckets
			    WHERE DATEADD(HOUR, 1, bucket_at) < :to
			),
			eligible_stuck_signals AS (
			    SELECT DISTINCT para.box_device_id,
			                    para.plc_address
			    FROM dbo.F2_Utility_Para para
			    INNER JOIN dbo.F2_Utility_Scada_Channel channel
			            ON channel.box_device_id = para.box_device_id
			    INNER JOIN dbo.F2_Utility_Scada sc
			            ON sc.scada_id = channel.scada_id
			    INNER JOIN dbo.F2_Utility_Alert_Master alert_rule
			            ON UPPER(LTRIM(RTRIM(alert_rule.ParameterCode))) =
			               UPPER(LTRIM(RTRIM(para.cate_id)))
			           AND alert_rule.IsActive = 1
			           AND UPPER(LTRIM(RTRIM(alert_rule.RuleType))) = 'STUCK'
			           AND alert_rule.WindowMinutes = :windowMinutes
			    WHERE (para.name_en IS NULL OR para.name_en NOT LIKE 'Slave%')
			      AND (:fac IS NULL OR sc.fac = :fac)
			      AND (:cate IS NULL OR channel.cate = :cate)
			      AND (:scadaId IS NULL OR sc.scada_id = :scadaId)
			      AND (:boxDeviceId IS NULL OR para.box_device_id = :boxDeviceId)
			)
			SELECT signal.box_device_id AS boxDeviceId,
			       signal.plc_address AS plcAddress,
			       bucket.bucket_at AS bucketAt,
			       :windowMinutes AS windowMinutes,
			       COUNT_BIG(history.id) AS sampleCount,
			       MIN(history.value) AS minValue,
			       MAX(history.value) AS maxValue,
			       AVG(history.value) AS avgValue,
			       SUM(history.value) AS sumValue
			FROM hourly_buckets bucket
			CROSS JOIN eligible_stuck_signals signal
			LEFT JOIN dbo.F2_Utility_Para_History history
			       ON history.box_device_id = signal.box_device_id
			      AND history.plc_address = signal.plc_address
			      AND history.recorded_at >= DATEADD(MINUTE, -1 * :windowMinutes, bucket.bucket_at)
			      AND history.recorded_at <= bucket.bucket_at
			GROUP BY signal.box_device_id,
			         signal.plc_address,
			         bucket.bucket_at
			OPTION (MAXRECURSION 32767)
			""", nativeQuery = true)
	List<UtilitySignalHistoricalWindowStatsProjection> findHistoricalWindowStats(
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("fac") String fac,
			@Param("cate") String cate,
			@Param("scadaId") String scadaId,
			@Param("boxDeviceId") String boxDeviceId,
			@Param("windowMinutes") int windowMinutes
	);
}
