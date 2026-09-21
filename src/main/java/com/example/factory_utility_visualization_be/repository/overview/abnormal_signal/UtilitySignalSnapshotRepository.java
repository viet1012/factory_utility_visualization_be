package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalSnapshotProjection;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalWindowStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Raw signal retrieval only. Alert policy belongs in UtilityAlertEvaluationService. */
public interface UtilitySignalSnapshotRepository extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			SELECT sc.fac AS fac,
			       sc.scada_id AS scadaId,
			       ch.cate AS cate,
			       pa.id AS paraId,
			       pa.cate_id AS parameterCode,
			       pa.name_en AS signalName,
			       pa.unit AS unit,
			       pa.box_device_id AS boxDeviceId,
			       pa.plc_address AS plcAddress,
			       cur.recorded_at AS recordedAt,
			       cur.value AS currentValue,
			       prev.value AS prevValue
			FROM dbo.F2_Utility_Para pa
			INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON ch.box_device_id = pa.box_device_id
			INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			OUTER APPLY (
			    SELECT TOP (1) hi.recorded_at, hi.value
			    FROM dbo.F2_Utility_Para_History hi
			    WHERE hi.box_device_id = pa.box_device_id
			      AND hi.plc_address = pa.plc_address
			    ORDER BY hi.recorded_at DESC
			) cur
			OUTER APPLY (
			    SELECT TOP (1) hi.value
			    FROM dbo.F2_Utility_Para_History hi
			    WHERE hi.box_device_id = pa.box_device_id
			      AND hi.plc_address = pa.plc_address
			      AND hi.recorded_at < cur.recorded_at
			    ORDER BY hi.recorded_at DESC
			) prev
			WHERE pa.name_en IS NULL OR pa.name_en NOT LIKE 'Slave%'
			ORDER BY sc.fac, ch.cate, sc.scada_id, pa.box_device_id, pa.plc_address
			""", nativeQuery = true)
	List<UtilitySignalSnapshotProjection> findLatestSnapshots();

	@Query(value = """
			SELECT pa.id AS paraId,
			       COUNT_BIG(hi.id) AS sampleCount,
			       MIN(hi.value) AS windowMinValue,
			       MAX(hi.value) AS windowMaxValue,
			       AVG(hi.value) AS windowAvgValue,
			       SUM(hi.value) AS windowSumValue
			FROM dbo.F2_Utility_Para pa
			LEFT JOIN dbo.F2_Utility_Para_History hi
			       ON hi.box_device_id = pa.box_device_id
			      AND hi.plc_address = pa.plc_address
			      AND hi.recorded_at >= DATEADD(MINUTE, -:windowMinutes, GETDATE())
			GROUP BY pa.id
			""", nativeQuery = true)
	List<UtilitySignalWindowStatsProjection> findWindowStats(
			@Param("windowMinutes") int windowMinutes
	);
}
