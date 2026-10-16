package com.example.factory_utility_visualization_be.repository.runtime;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageHourProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Queries for GET /api/utility/power-usage. Hourly History_Main only, no raw.
 */
public interface PowerUsageRepo extends JpaRepository<DummyEntity, Long> {

	// Energy consumption per fac / SOLAR flag / purpose cabinet / hour. A
	// History_Main row holds the consumption of the hour ENDING at pick_at, so
	// hourAt = pick_at hour - 1h (same as /series/history COUNTER); the caller
	// shifts fromTs/toTs by +1h. Same filters as the KVH daily data: a
	// box_device_id with any box_id = 'SOLAR' channel is SOLAR, every other
	// one is grid; MTD = 'MTD' and value > 0. purposeKey = 'FAC|BOX_ID'
	// (upper case) when the device has a channel listed in :purposeKeys, else
	// NULL. Para rows are deduplicated so duplicates do not double count.
	@Query(value = """
			WITH DeviceMap AS (
			    SELECT
			        d.box_device_id,
			        MAX(d.fac) AS fac,
			        MAX(CASE WHEN d.box_key = 'SOLAR' THEN 1 ELSE 0 END) AS is_solar,
			        MAX(CASE WHEN d.purpose_key IN (:purposeKeys) THEN d.purpose_key END) AS purpose_key
			    FROM (
			        SELECT
			            ch.box_device_id,
			            sc.fac,
			            UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) AS box_key,
			            CONCAT(
			                UPPER(LTRIM(RTRIM(ISNULL(sc.fac, '')))),
			                '|',
			                UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, ''))))
			            ) AS purpose_key
			        FROM dbo.F2_Utility_Scada_Channel ch
			        INNER JOIN dbo.F2_Utility_Scada sc
			                ON sc.scada_id = ch.scada_id
			    ) d
			    GROUP BY d.box_device_id
			),
			EnergyPara AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			    FROM dbo.F2_Utility_Para pa
			    WHERE pa.name_en = :energyName
			)
			SELECT
			    e.fac                              AS fac,
			    e.is_solar                         AS solar,
			    e.purpose_key                      AS purposeKey,
			    e.hour_at                          AS hourAt,
			    CAST(SUM(e.kwh) AS DECIMAL(19, 4)) AS kwh
			FROM (
			    SELECT
			        dm.fac,
			        dm.is_solar,
			        dm.purpose_key,
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, hi.pick_at) - 1, 0) AS hour_at,
			        CAST(hi.[value] AS DECIMAL(19, 6))                  AS kwh
			    FROM dbo.F2_Utility_Para_History_Main hi
			    INNER JOIN EnergyPara ep
			            ON ep.box_device_id = hi.box_device_id
			           AND ep.plc_address = hi.plc_address
			    INNER JOIN DeviceMap dm
			            ON dm.box_device_id = hi.box_device_id
			    WHERE hi.pick_at >= :fromTs
			      AND hi.pick_at <  :toTs
			      AND hi.[value] > 0
			      AND ISNULL(hi.MTD, '') = 'MTD'
			) e
			GROUP BY e.fac, e.is_solar, e.purpose_key, e.hour_at
			ORDER BY e.hour_at
			""", nativeQuery = true)
	List<PowerUsageHourProjection> findHourlyEnergy(
			@Param("energyName") String energyName,
			@Param("purposeKeys") List<String> purposeKeys,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs
	);

	// Tariff rows (same table as the monthly / hourly cost queries):
	// WD '1' = Sunday, '2-7' = Monday..Saturday; frTime/toTime in hours of
	// day (frTime > toTime = overnight); vnd = VND/kWh.
	@Query(value = """
			SELECT
			    LTRIM(RTRIM(c.WD))              AS wd,
			    CAST(c.frTime AS DECIMAL(9, 4)) AS frTime,
			    CAST(c.toTime AS DECIMAL(9, 4)) AS toTime,
			    CAST(c.vnd AS DECIMAL(19, 4))   AS vnd
			FROM dbo.F2_Utility_Cost_Master c
			""", nativeQuery = true)
	List<PowerUsageTariffProjection> findTariffs();
}
