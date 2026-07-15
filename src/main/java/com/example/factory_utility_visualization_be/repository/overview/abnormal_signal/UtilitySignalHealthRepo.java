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
			
			Agg15 AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			        COUNT(*) AS cnt_15m,
			        MIN(hi.value) AS min_value,
			        MAX(hi.value) AS max_value,
			        AVG(hi.value) AS avg_value,
			        SUM(hi.value) AS sum_value
			    FROM dbo.F2_Utility_Para_History hi
			    WHERE hi.recorded_at >= DATEADD(MINUTE, -60, GETDATE())
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
			),
			
			ScadaPipelinePressureAgg15 AS (
			    SELECT
			        sc.scada_id,
			        SUM(hi.value) AS scada_sum_value
			    FROM dbo.F2_Utility_Para_History hi
			
			    INNER JOIN dbo.F2_Utility_Para pa
			        ON hi.box_device_id = pa.box_device_id
			       AND hi.plc_address = pa.plc_address
			
			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON hi.box_device_id = ch.box_device_id
			
			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id
			
			    WHERE hi.recorded_at >= DATEADD(MINUTE, -60, GETDATE())
			      AND pa.name_en = 'Data Pipeline pressure'
			
			    GROUP BY
			        sc.scada_id
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
			        WHEN l.recorded_at < DATEADD(MINUTE, -60, GETDATE())
			            THEN 'NO_DATA'
			
			        WHEN pa.name_en = 'Temperure data'
			             AND l.value >= 60
			            THEN 'HIGH_TEMPERATURE'
			
			        WHEN pa.name_en = 'Humity data'
			             AND l.value > 70
			            THEN 'HIGH_HUMIDITY'
			
					WHEN pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
					     AND l.value = 0
					     AND NOT (
					         l.box_device_id = 'P-1.1_MFM384'
					         AND pa.name_en = 'Current I3'
					     )
					    THEN 'Current is 0, CT may be broken'
			
			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND l.value <= 0
			            THEN 'INVALID_VALUE'
			
			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND ISNULL(a.cnt_15m, 0) > 1
			             AND a.min_value = a.max_value
			            THEN 'STUCK_VALUE'
			
			        WHEN pa.name_en LIKE 'Average Power Factor%'
			             AND (l.value < -1 OR l.value > 1)
			            THEN 'INVALID_POWER_FACTOR'
			
			        WHEN pa.name_en = 'Sensor compressed air pressure Data'
			             AND ISNULL(a.sum_value, l.value) < 0
			            THEN 'NEGATIVE_PRESSURE'
			
			        WHEN pa.name_en = 'Data Pipeline pressure'
			             AND ISNULL(spa.scada_sum_value, l.value) < 0
			            THEN 'NEGATIVE_PRESSURE'
			
			        WHEN pa.name_en = 'Cooling tank temperature data'
			             AND ISNULL(a.avg_value, l.value) > 35
			            THEN 'HIGH_COOLING_TANK_TEMP'
			
					WHEN pa.name_en NOT LIKE 'Average Power Factor%'
					     AND pa.name_en NOT IN (
					         'Data Pipeline pressure',
					         'Sensor compressed air pressure Data'
					     )
					     AND l.value < 0
					    THEN 'NEGATIVE_VALUE'
			
			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN 'ABNORMAL_JUMP'
			
			        ELSE 'OK'
			    END AS status,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -60, GETDATE())
			            THEN 'No update for more than 60 minutes'
			
			        WHEN pa.name_en = 'Temperure data'
			             AND l.value >= 60
			            THEN 'Cabinet temperature is abnormal, >= 60C'
			
			        WHEN pa.name_en = 'Humity data'
			             AND l.value > 70
			            THEN 'Humidity is too high, risk of condensation'
			
			        WHEN pa.name_en IN ('Voltage V12', 'Voltage V23', 'Voltage V31')
			             AND (l.value < 198 OR l.value > 242)
			            THEN 'Voltage is outside normal range 198VAC - 242VAC'
			
					WHEN pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
					     AND l.value = 0
					     AND NOT (
					         l.box_device_id = 'P-1.1_MFM384'
					         AND pa.name_en = 'Current I3'
					     )
					    THEN 'Current is 0, CT may be broken'
			
			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND l.value <= 0
			            THEN 'Value must be greater than 0 and not negative'
			
			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND ISNULL(a.cnt_15m, 0) > 1
			             AND a.min_value = a.max_value
			            THEN 'Value has not changed for 15 minutes'
			
			        WHEN pa.name_en LIKE 'Average Power Factor%'
			             AND (l.value < -1 OR l.value > 1)
			            THEN 'Average Power Factor must be between -1 and 1'
			
			        WHEN pa.name_en = 'Sensor compressed air pressure Data'
			             AND ISNULL(a.sum_value, l.value) < 0
			            THEN 'Compressed air pressure sum is negative'
			
			        WHEN pa.name_en = 'Data Pipeline pressure'
			             AND ISNULL(spa.scada_sum_value, l.value) < 0
			            THEN 'Data Pipeline pressure sum by SCADA is negative'
			
			        WHEN pa.name_en = 'Cooling tank temperature data'
			             AND ISNULL(a.avg_value, l.value) > 35
			            THEN 'Cooling tank average temperature is greater than 35C'
			
					WHEN pa.name_en NOT LIKE 'Average Power Factor%'
					     AND pa.name_en NOT IN (
					         'Data Pipeline pressure',
					         'Sensor compressed air pressure Data'
					     )
					     AND l.value < 0
					    THEN 'Current value is negative'
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
			
			LEFT JOIN Agg15 a
			    ON l.box_device_id = a.box_device_id
			   AND l.plc_address = a.plc_address
			
			INNER JOIN ParaUnique pa
			    ON l.box_device_id = pa.box_device_id
			   AND l.plc_address = pa.plc_address
			
			INNER JOIN dbo.F2_Utility_Scada_Channel ch
			    ON l.box_device_id = ch.box_device_id
			
			INNER JOIN dbo.F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id
			
			LEFT JOIN ScadaPipelinePressureAgg15 spa
			    ON sc.scada_id = spa.scada_id
			
			WHERE l.rn_desc = 1
			
			ORDER BY
			    sc.fac ASC,
			    ch.cate ASC,
			    sc.scada_id ASC,
			    l.box_device_id ASC,
			
			    CASE
			        WHEN l.recorded_at < DATEADD(MINUTE, -15, GETDATE())
			          OR (pa.name_en = 'Temperure data' AND l.value >= 60)
			          OR (pa.name_en = 'Humity data' AND l.value > 70)
			          OR (pa.name_en IN ('Voltage V12', 'Voltage V23', 'Voltage V31') AND (l.value < 198 OR l.value > 242))
						OR (
						    pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
						    AND l.value = 0
						    AND NOT (
						        l.box_device_id = 'P-1.1_MFM384'
						        AND pa.name_en = 'Current I3'
						    )
						)
			          OR (pa.name_en IN ('Total Power', 'Total Energy Consumption') AND l.value <= 0)
			          OR (pa.name_en IN ('Total Power', 'Total Energy Consumption') AND ISNULL(a.cnt_15m, 0) > 1 AND a.min_value = a.max_value)
			          OR (pa.name_en LIKE 'Average Power Factor%' AND (l.value < -1 OR l.value > 1))
			          OR (pa.name_en = 'Sensor compressed air pressure Data' AND ISNULL(a.sum_value, l.value) < 0)
			          OR (pa.name_en = 'Data Pipeline pressure' AND ISNULL(spa.scada_sum_value, l.value) < 0)
			          OR (pa.name_en = 'Cooling tank temperature data' AND ISNULL(a.avg_value, l.value) > 35)
					  OR (
					    pa.name_en NOT LIKE 'Average Power Factor%'
					    AND pa.name_en NOT IN (
					        'Data Pipeline pressure',
					        'Sensor compressed air pressure Data'
					    )
					    AND l.value < 0
					  )
			          OR (l.prev_value IS NOT NULL AND ABS(l.value - l.prev_value) > 1000)
			        THEN 0
			        ELSE 1
			    END ASC,
			
			    ABS(ISNULL(l.value - l.prev_value, 0)) DESC,
			    pa.name_en ASC
			""", nativeQuery = true)
	List<UtilitySignalHealthMatrixProjection> findSignalHealthMatrix1();

	@Query(value = """
			WITH Agg15 AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,
			        COUNT(*) AS cnt_15m,
			        MIN(hi.value) AS min_value,
			        MAX(hi.value) AS max_value,
			        AVG(hi.value) AS avg_value,
			        SUM(hi.value) AS sum_value
			    FROM dbo.F2_Utility_Para_History hi
			    WHERE hi.recorded_at >= DATEADD(MINUTE, -60, GETDATE())
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
			),

			ScadaPipelinePressureAgg15 AS (
			    SELECT
			        sc.scada_id,
			        SUM(hi.value) AS scada_sum_value
			    FROM dbo.F2_Utility_Para_History hi

			    INNER JOIN dbo.F2_Utility_Para pa
			        ON hi.box_device_id = pa.box_device_id
			       AND hi.plc_address = pa.plc_address

			    INNER JOIN dbo.F2_Utility_Scada_Channel ch
			        ON hi.box_device_id = ch.box_device_id

			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON ch.scada_id = sc.scada_id

			    WHERE hi.recorded_at >= DATEADD(MINUTE, -60, GETDATE())
			      AND pa.name_en = 'Data Pipeline pressure'

			    GROUP BY
			        sc.scada_id
			),

			-- Với mỗi điểm đo, seek thẳng vào đúng 1-2 dòng mới nhất qua index
			-- (box_device_id, plc_address, recorded_at DESC) thay vì bắt SQL Server
			-- sort toàn bộ bảng History để tính LAG()/ROW_NUMBER() như bản cũ.
			-- OUTER APPLY hoạt động như LEFT JOIN: nếu thiết bị chưa từng ghi log
			-- nào (hoặc log đã bị purge hết), vẫn trả ra 1 dòng với giá trị NULL
			-- thay vì làm thiết bị đó biến mất khỏi kết quả.
			Latest AS (
			    SELECT
			        pa.box_device_id,
			        pa.plc_address,
			        cur.recorded_at,
			        cur.value,
			        prev.value AS prev_value
			    FROM ParaUnique pa

			    OUTER APPLY (
			        SELECT TOP (1) hi.recorded_at, hi.value
			        FROM dbo.F2_Utility_Para_History hi
			        WHERE hi.box_device_id = pa.box_device_id
			          AND hi.plc_address = pa.plc_address
			        ORDER BY hi.recorded_at DESC
			    ) cur

			    OUTER APPLY (
			        SELECT TOP (1) hi2.value
			        FROM dbo.F2_Utility_Para_History hi2
			        WHERE hi2.box_device_id = pa.box_device_id
			          AND hi2.plc_address = pa.plc_address
			          AND hi2.recorded_at < cur.recorded_at
			        ORDER BY hi2.recorded_at DESC
			    ) prev
			)

			SELECT
			    sc.fac AS fac,
			    sc.scada_id AS scadaId,
			    ch.cate AS cate,
			    pa.name_en AS signalName,
			    pa.unit AS unit,
			    pa.box_device_id AS boxDeviceId,
			    pa.plc_address AS plcAddress,
			    l.recorded_at AS recordedAt,
			    l.value AS currentValue,
			    l.prev_value AS prevValue,
			    ABS(ISNULL(l.value - l.prev_value, 0)) AS jumpSize,

			    CASE
			        -- Thiết bị chưa từng có dữ liệu (OUTER APPLY ra NULL), hoặc dòng mới
			        -- nhất vẫn cũ hơn 60 phút -> đều tính là NO_DATA.
			        WHEN l.recorded_at IS NULL
			             OR l.recorded_at < DATEADD(MINUTE, -60, GETDATE())
			            THEN 'NO_DATA'

			        WHEN pa.name_en = 'Temperure data'
			             AND l.value >= 60
			            THEN 'HIGH_TEMPERATURE'

			        WHEN pa.name_en = 'Humity data'
			             AND l.value > 70
			            THEN 'HIGH_HUMIDITY'

					WHEN pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
					     AND l.value = 0
					     AND NOT (
					         l.box_device_id = 'P-1.1_MFM384'
					         AND pa.name_en = 'Current I3'
					     )
					    THEN 'Current is 0, CT may be broken'

			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND l.value <= 0
			            THEN 'INVALID_VALUE'

			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND ISNULL(a.cnt_15m, 0) > 1
			             AND a.min_value = a.max_value
			            THEN 'STUCK_VALUE'

			        WHEN pa.name_en LIKE 'Average Power Factor%'
			             AND (l.value < -1 OR l.value > 1)
			            THEN 'INVALID_POWER_FACTOR'

			        WHEN pa.name_en = 'Sensor compressed air pressure Data'
			             AND ISNULL(a.sum_value, l.value) < 0
			            THEN 'NEGATIVE_PRESSURE'

			        WHEN pa.name_en = 'Data Pipeline pressure'
			             AND ISNULL(spa.scada_sum_value, l.value) < 0
			            THEN 'NEGATIVE_PRESSURE'

			        WHEN pa.name_en = 'Cooling tank temperature data'
			             AND ISNULL(a.avg_value, l.value) > 35
			            THEN 'HIGH_COOLING_TANK_TEMP'

					WHEN pa.name_en NOT LIKE 'Average Power Factor%'
					     AND pa.name_en NOT IN (
					         'Data Pipeline pressure',
					         'Sensor compressed air pressure Data'
					     )
					     AND l.value < 0
					    THEN 'NEGATIVE_VALUE'

			        WHEN l.prev_value IS NOT NULL
			             AND ABS(l.value - l.prev_value) > 1000
			            THEN 'ABNORMAL_JUMP'

			        ELSE 'OK'
			    END AS status,

			    CASE
			        WHEN l.recorded_at IS NULL
			             OR l.recorded_at < DATEADD(MINUTE, -60, GETDATE())
			            THEN 'No update for more than 60 minutes'

			        WHEN pa.name_en = 'Temperure data'
			             AND l.value >= 60
			            THEN 'Cabinet temperature is abnormal, >= 60C'

			        WHEN pa.name_en = 'Humity data'
			             AND l.value > 70
			            THEN 'Humidity is too high, risk of condensation'

			        WHEN pa.name_en IN ('Voltage V12', 'Voltage V23', 'Voltage V31')
			             AND (l.value < 198 OR l.value > 242)
			            THEN 'Voltage is outside normal range 198VAC - 242VAC'

					WHEN pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
					     AND l.value = 0
					     AND NOT (
					         l.box_device_id = 'P-1.1_MFM384'
					         AND pa.name_en = 'Current I3'
					     )
					    THEN 'Current is 0, CT may be broken'

			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND l.value <= 0
			            THEN 'Value must be greater than 0 and not negative'

			        WHEN pa.name_en IN ('Total Power', 'Total Energy Consumption')
			             AND ISNULL(a.cnt_15m, 0) > 1
			             AND a.min_value = a.max_value
			            THEN 'Value has not changed for 15 minutes'

			        WHEN pa.name_en LIKE 'Average Power Factor%'
			             AND (l.value < -1 OR l.value > 1)
			            THEN 'Average Power Factor must be between -1 and 1'

			        WHEN pa.name_en = 'Sensor compressed air pressure Data'
			             AND ISNULL(a.sum_value, l.value) < 0
			            THEN 'Compressed air pressure sum is negative'

			        WHEN pa.name_en = 'Data Pipeline pressure'
			             AND ISNULL(spa.scada_sum_value, l.value) < 0
			            THEN 'Data Pipeline pressure sum by SCADA is negative'

			        WHEN pa.name_en = 'Cooling tank temperature data'
			             AND ISNULL(a.avg_value, l.value) > 35
			            THEN 'Cooling tank average temperature is greater than 35C'

					WHEN pa.name_en NOT LIKE 'Average Power Factor%'
					     AND pa.name_en NOT IN (
					         'Data Pipeline pressure',
					         'Sensor compressed air pressure Data'
					     )
					     AND l.value < 0
					    THEN 'Current value is negative'
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

			-- Driving table là ParaUnique (LEFT JOIN sang Latest), để thiết bị hoàn
			-- toàn không có dữ liệu (đã bị purge hết, hoặc chưa từng ghi) vẫn hiện
			-- ra với NO_DATA thay vì biến mất khỏi kết quả.
			FROM ParaUnique pa

			LEFT JOIN Latest l
			    ON l.box_device_id = pa.box_device_id
			   AND l.plc_address = pa.plc_address

			LEFT JOIN Agg15 a
			    ON pa.box_device_id = a.box_device_id
			   AND pa.plc_address = a.plc_address

			INNER JOIN dbo.F2_Utility_Scada_Channel ch
			    ON pa.box_device_id = ch.box_device_id

			INNER JOIN dbo.F2_Utility_Scada sc
			    ON ch.scada_id = sc.scada_id

			LEFT JOIN ScadaPipelinePressureAgg15 spa
			    ON sc.scada_id = spa.scada_id

			ORDER BY
			    sc.fac ASC,
			    ch.cate ASC,
			    sc.scada_id ASC,
			    pa.box_device_id ASC,

			    CASE
			        WHEN l.recorded_at IS NULL
			          OR l.recorded_at < DATEADD(MINUTE, -15, GETDATE())
			          OR (pa.name_en = 'Temperure data' AND l.value >= 60)
			          OR (pa.name_en = 'Humity data' AND l.value > 70)
			          OR (pa.name_en IN ('Voltage V12', 'Voltage V23', 'Voltage V31') AND (l.value < 198 OR l.value > 242))
						OR (
						    pa.name_en IN ('Current I1', 'Current I2', 'Current I3')
						    AND l.value = 0
						    AND NOT (
						        l.box_device_id = 'P-1.1_MFM384'
						        AND pa.name_en = 'Current I3'
						    )
						)
			          OR (pa.name_en IN ('Total Power', 'Total Energy Consumption') AND l.value <= 0)
			          OR (pa.name_en IN ('Total Power', 'Total Energy Consumption') AND ISNULL(a.cnt_15m, 0) > 1 AND a.min_value = a.max_value)
			          OR (pa.name_en LIKE 'Average Power Factor%' AND (l.value < -1 OR l.value > 1))
			          OR (pa.name_en = 'Sensor compressed air pressure Data' AND ISNULL(a.sum_value, l.value) < 0)
			          OR (pa.name_en = 'Data Pipeline pressure' AND ISNULL(spa.scada_sum_value, l.value) < 0)
			          OR (pa.name_en = 'Cooling tank temperature data' AND ISNULL(a.avg_value, l.value) > 35)
					  OR (
					    pa.name_en NOT LIKE 'Average Power Factor%'
					    AND pa.name_en NOT IN (
					        'Data Pipeline pressure',
					        'Sensor compressed air pressure Data'
					    )
					    AND l.value < 0
					  )
			          OR (l.prev_value IS NOT NULL AND ABS(l.value - l.prev_value) > 1000)
			        THEN 0
			        ELSE 1
			    END ASC,

			    ABS(ISNULL(l.value - l.prev_value, 0)) DESC,
			    pa.name_en ASC
			""", nativeQuery = true)
	List<UtilitySignalHealthMatrixProjection> findSignalHealthMatrix();
}