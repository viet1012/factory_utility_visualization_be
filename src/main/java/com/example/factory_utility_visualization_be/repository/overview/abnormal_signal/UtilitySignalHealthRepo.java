package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal;


import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilityAbnormalSignalProjection;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilitySignalHealthMatrixProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UtilitySignalHealthRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			WITH H AS (
			SELECT
				hi.box_device_id,
				hi.plc_address,
				hi.recorded_at,
				hi.value,
			
			       LAG(hi.value) OVER (
			           PARTITION BY hi.box_device_id, hi.plc_address
			           ORDER BY hi.recorded_at
			       ) AS prev_value,
			
			       ROW_NUMBER() OVER (
			           PARTITION BY hi.box_device_id, hi.plc_address
			           ORDER BY hi.recorded_at DESC
			       ) AS rn_desc
			   FROM dbo.F2_Utility_Para_History hi	),
			
			StuckCheck AS (
			    SELECT
			        box_device_id,
			        plc_address,
			        MIN(value) AS min_value,
			        MAX(value) AS max_value
			    FROM H
			    WHERE rn_desc <= 5
			    GROUP BY
			        box_device_id,
			        plc_address
			),
			
			ParaUnique AS (
			    SELECT
			        box_device_id,
			        plc_address,
			        MAX(name_en) AS name_en
			    FROM dbo.F2_Utility_Para
			    WHERE name_en NOT LIKE 'Slave%'
			    GROUP BY
			        box_device_id,
			        plc_address
			)
			
			SELECT
			    sc.fac AS fac,
			
			    sc.scada_id AS scadaId,
			
			    ch.cate AS cate,
			
			    pa.name_en AS signalName,
			
			    l.box_device_id AS boxDeviceId,
			
			    l.plc_address AS plcAddress,
			
			    l.recorded_at AS recordedAt,
			
			    l.value AS currentValue,
			
			    l.prev_value AS prevValue,
			
			    ABS(ISNULL(l.value - l.prev_value, 0)) AS jumpSize,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			            THEN 'NO_DATA'
			
			        WHEN l.value < 0
			            THEN 'NEGATIVE_VALUE'
			
			        WHEN (
			                pa.name_en IN ('Temperure data', 'Humity data')
			                AND s.min_value = s.max_value
			                AND l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			             )
			             OR (
			                pa.name_en NOT IN ('Temperure data', 'Humity data')
			                AND s.min_value = s.max_value
			             )
			            THEN 'STUCK_VALUE'
			
			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN 'ABNORMAL_JUMP'
			
			        ELSE 'OK'
			    END AS status,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			            THEN 'No update for more than 5 minutes'
			
			        WHEN l.value < 0
			            THEN 'Current value is negative'
			
			        WHEN (
			                pa.name_en IN ('Temperure data', 'Humity data')
			                AND s.min_value = s.max_value
			                AND l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			             )
			             OR (
			                pa.name_en NOT IN ('Temperure data', 'Humity data')
			                AND s.min_value = s.max_value
			             )
			            THEN 'Last 5 readings are identical'
			
			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN CONCAT(
			                'Jump detected: ',
			                CAST(l.prev_value AS VARCHAR(50)),
			                ' -> ',
			                CAST(l.value AS VARCHAR(50))
			            )
			
			        ELSE 'Normal'
			    END AS description
			
			FROM H l
			
			LEFT JOIN StuckCheck s
			    ON l.box_device_id = s.box_device_id
			   AND l.plc_address = s.plc_address
			
			INNER JOIN ParaUnique pa
			    ON l.box_device_id = pa.box_device_id
			   AND l.plc_address = pa.plc_address
			
			INNER JOIN dbo.F2_Utility_Scada_Channel ch
			    ON l.box_device_id = ch.box_device_id
			   -- AND l.plc_address = ch.plc_address
			   -- mở dòng này nếu bảng Channel có plc_address
			
			INNER JOIN dbo.F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id
			
			WHERE l.rn_desc = 1
			  AND (
			        l.recorded_at < DATEADD(MINUTE,-5,GETDATE())
			     OR l.value < 0
			     OR s.min_value = s.max_value
			     OR (
			            l.prev_value IS NOT NULL
			        AND ABS(l.value - l.prev_value) > 1000
			     )
			  )
			
			ORDER BY
			CASE
			    WHEN l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			        THEN 1
			
			    WHEN l.value < 0
			        THEN 2
			
			    WHEN (
			            pa.name_en IN ('Temperure data', 'Humity data')
			            AND s.min_value = s.max_value
			            AND l.recorded_at < DATEADD(MINUTE, -5, GETDATE())
			         )
			         OR (
			            pa.name_en NOT IN ('Temperure data', 'Humity data')
			            AND s.min_value = s.max_value
			         )
			        THEN 3
			
			    WHEN l.prev_value IS NOT NULL
			         AND ABS(l.value - l.prev_value) > 1000
			        THEN 4
			
			    ELSE 99
			END ASC,
			
			ABS(ISNULL(l.value - l.prev_value, 0)) DESC,
			
			sc.fac ASC,
			
			ch.cate ASC,
			
			sc.scada_id ASC,
			
			l.box_device_id ASC,
			
			pa.name_en ASC
			
			""", nativeQuery = true)
	List<UtilityAbnormalSignalProjection> findAbnormalSignals();


	@Query(value = """
			WITH H AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			        hi.recorded_at,
			        hi.value,
			
			        LAG(hi.value) OVER (
			            PARTITION BY hi.box_device_id, hi.plc_address
			            ORDER BY hi.recorded_at
			        ) AS prev_value,
			
			        ROW_NUMBER() OVER (
			            PARTITION BY hi.box_device_id, hi.plc_address
			            ORDER BY hi.recorded_at DESC
			        ) AS rn_desc
			    FROM dbo.F2_Utility_Para_History hi
			),
			
			StuckCheck AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			        COUNT(*) AS cnt_15m,
			        MIN(hi.value) AS min_value,
			        MAX(hi.value) AS max_value
			    FROM dbo.F2_Utility_Para_History hi
			    WHERE hi.recorded_at >= DATEADD(MINUTE, -15, GETDATE())
			    GROUP BY
			        hi.box_device_id,
			        hi.plc_address
			),
			
			ParaUnique AS (
			    SELECT
			        box_device_id,
			        plc_address,
			        MAX(name_en) AS name_en,
			        MAX(unit) AS unit
			    FROM dbo.F2_Utility_Para
			    WHERE name_en NOT LIKE 'Slave%'
			      AND name_en NOT IN (
			          'Total Apparent Power',
			          'Total Reactive Power',
			          'Total Reactive Energy'
			      )
			    GROUP BY
			        box_device_id,
			        plc_address
			)
			
			SELECT
			    sc.fac AS fac,
			    sc.scada_id AS scadaId,
			    ch.cate AS cate,
			    pa.name_en AS signalName,
			    pa.unit AS unit,
			    l.box_device_id AS boxDeviceId,
			    l.plc_address AS plcAddress,
			    l.recorded_at AS recordedAt,
			    l.value AS currentValue,
			    l.prev_value AS prevValue,
			    ABS(ISNULL(l.value - l.prev_value, 0)) AS jumpSize,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -15, GETDATE())
			            THEN 'NO_DATA'
			
			        WHEN l.value < 0
			            THEN 'NEGATIVE_VALUE'
			
			        WHEN l.recorded_at >= DATEADD(MINUTE, -15, GETDATE())
			             AND ISNULL(s.cnt_15m, 0) > 1
			             AND s.min_value = s.max_value
			            THEN 'STUCK_VALUE'
			
			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN 'ABNORMAL_JUMP'
			
			        ELSE 'OK'
			    END AS status,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -15, GETDATE())
			            THEN 'No update for more than 15 minutes'
			
			        WHEN l.value < 0
			            THEN 'Current value is negative'
			
			        WHEN l.recorded_at >= DATEADD(MINUTE, -15, GETDATE())
			             AND ISNULL(s.cnt_15m, 0) > 1
			             AND s.min_value = s.max_value
			            THEN 'Value has not changed for 15 minutes'
			
			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN CONCAT(
			                'Jump detected: ',
			                CAST(l.prev_value AS VARCHAR(50)),
			                ' -> ',
			                CAST(l.value AS VARCHAR(50))
			            )
			
			        ELSE 'Normal'
			    END AS description
			
			FROM H l
			
			LEFT JOIN StuckCheck s
			    ON l.box_device_id = s.box_device_id
			   AND l.plc_address = s.plc_address
			
			INNER JOIN ParaUnique pa
			    ON l.box_device_id = pa.box_device_id
			   AND l.plc_address = pa.plc_address
			
			INNER JOIN dbo.F2_Utility_Scada_Channel ch
			    ON l.box_device_id = ch.box_device_id
			
			INNER JOIN dbo.F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id
			
			WHERE l.rn_desc = 1
			
			ORDER BY
			    sc.fac ASC,
			    ch.cate ASC,
			    sc.scada_id ASC,
			    l.box_device_id ASC,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -15, GETDATE())
			          OR l.value < 0
			          OR (
			              l.recorded_at >= DATEADD(MINUTE, -15, GETDATE())
			              AND ISNULL(s.cnt_15m, 0) > 1
			              AND s.min_value = s.max_value
			          )
			          OR (
			              l.prev_value IS NOT NULL
			              AND ABS(l.value - l.prev_value) > 1000
			          )
			        THEN 0
			        ELSE 1
			    END ASC,
			
			    ABS(ISNULL(l.value - l.prev_value, 0)) DESC,
			    pa.name_en ASC
			""", nativeQuery = true)
	List<UtilitySignalHealthMatrixProjection> findSignalHealthMatrix();
}