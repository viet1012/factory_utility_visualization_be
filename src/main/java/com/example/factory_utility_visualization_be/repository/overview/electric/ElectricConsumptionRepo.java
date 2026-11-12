package com.example.factory_utility_visualization_be.repository.overview.electric;

import com.example.factory_utility_visualization_be.model.DummyEntity;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionHourProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hourly energy for GET /api/utility/electric-consumption. Same meters and
 * filters as PowerUsageRepo.findHourlyEnergy (SOLAR devices = any channel
 * with box_id 'SOLAR', MTD = 'MTD', value > 0, hourAt = pick_at hour - 1h;
 * the caller shifts fromTs/toTs by +1h), plus an outlier flag per meter-hour:
 *
 * A meter-hour is an outlier when kWh > :minOutlierKwh AND kWh > :outlierRatio
 * x the median kWh/hour of that meter in the queried window (SOLAR meters:
 * kWh > :solarOutlierRatio x P90, since their many small night values make
 * the median far below the real midday output). This drops
 * cumulative register values written as hourly consumption (e.g. P-6.1
 * 10585 -> 24602 kWh in Jun 2026) and catch-up values written after a data
 * gap (e.g. many meters jumping at 2026-06-23 17:00).
 *
 * :excludedMeters are skipped (duplicate meters, e.g. P-3.1 = P-3);
 * :facAbMeters / :facCMeters move a meter to scope FAC_AB / FAC_C (returned
 * as fac 'FAC_AB' / 'FAC_C') instead of its SCADA fac, so the scopes follow
 * the transformers (Trans No.2 = A&B, Trans No.1 = C).
 */
public interface ElectricConsumptionRepo extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			-- per-device filters (excluded meters, transformer scope) are applied
			-- here once per meter instead of on every History_Main row
			WITH DeviceMap AS (
			    SELECT
			        ch.box_device_id,
			        CASE
			            WHEN UPPER(LTRIM(RTRIM(ch.box_device_id))) IN (:facAbMeters) THEN 'FAC_AB'
			            WHEN UPPER(LTRIM(RTRIM(ch.box_device_id))) IN (:facCMeters)  THEN 'FAC_C'
			            ELSE MAX(sc.fac)
			        END AS fac,
			        MAX(CASE WHEN UPPER(LTRIM(RTRIM(ISNULL(ch.box_id, '')))) = 'SOLAR' THEN 1 ELSE 0 END) AS is_solar
			    FROM dbo.F2_Utility_Scada_Channel ch
			    INNER JOIN dbo.F2_Utility_Scada sc
			            ON sc.scada_id = ch.scada_id
			    WHERE UPPER(LTRIM(RTRIM(ch.box_device_id))) NOT IN (:excludedMeters)
			    GROUP BY ch.box_device_id
			),
			EnergyPara AS (
			    SELECT DISTINCT
			        pa.box_device_id,
			        pa.plc_address
			    FROM dbo.F2_Utility_Para pa
			    WHERE pa.name_en = :energyName
			),
			DeviceHour AS (
			    SELECT
			        hi.box_device_id,
			        dm.fac,
			        dm.is_solar,
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, hi.pick_at) - 1, 0) AS hour_at,
			        SUM(CAST(hi.[value] AS DECIMAL(19, 6)))            AS kwh
			    FROM dbo.F2_Utility_Para_History_Main hi
			    INNER JOIN EnergyPara ep
			            ON ep.box_device_id = hi.box_device_id
			           AND ep.plc_address = hi.plc_address
			    INNER JOIN DeviceMap dm
			            ON dm.box_device_id = hi.box_device_id
			    WHERE hi.pick_at >= :fromTs
			      AND hi.pick_at <  :toTs
			      AND hi.[value] > 0
			      -- same as ISNULL(MTD, '') = 'MTD' (NULL never equals 'MTD'), but sargable
			      AND hi.MTD = 'MTD'
			    GROUP BY
			        hi.box_device_id,
			        dm.fac,
			        dm.is_solar,
			        DATEADD(HOUR, DATEDIFF(HOUR, 0, hi.pick_at) - 1, 0)
			),
			WithMedian AS (
			    SELECT
			        dh.*,
			        PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY dh.kwh)
			            OVER (PARTITION BY dh.box_device_id) AS median_kwh,
			        PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY dh.kwh)
			            OVER (PARTITION BY dh.box_device_id) AS p90_kwh
			    FROM DeviceHour dh
			),
			Marked AS (
			    SELECT
			        wm.*,
			        CASE
			            WHEN wm.kwh <= :minOutlierKwh THEN 0
			            -- SOLAR: many tiny night values pull the median down, so
			            -- compare with P90 (close to the midday peak) instead
			            WHEN wm.is_solar = 1
			             AND wm.kwh > wm.p90_kwh * :solarOutlierRatio THEN 1
			            WHEN wm.is_solar = 0
			             AND wm.kwh > wm.median_kwh * :outlierRatio THEN 1
			            ELSE 0
			        END AS is_outlier
			    FROM WithMedian wm
			)
			SELECT
			    m.fac                              AS fac,
			    m.is_solar                         AS solar,
			    m.hour_at                          AS hourAt,
			    m.is_outlier                       AS outlier,
			    CAST(SUM(m.kwh) AS DECIMAL(19, 4)) AS kwh,
			    COUNT(DISTINCT m.box_device_id)    AS devices
			FROM Marked m
			GROUP BY m.fac, m.is_solar, m.hour_at, m.is_outlier
			ORDER BY m.hour_at
			""", nativeQuery = true)
	List<ElectricConsumptionHourProjection> findHourlyEnergy(
			@Param("energyName") String energyName,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			@Param("outlierRatio") BigDecimal outlierRatio,
			@Param("solarOutlierRatio") BigDecimal solarOutlierRatio,
			@Param("minOutlierKwh") BigDecimal minOutlierKwh,
			// upper-case box_device_id lists; never empty (pass List.of("") for none)
			@Param("excludedMeters") List<String> excludedMeters,
			@Param("facAbMeters") List<String> facAbMeters,
			@Param("facCMeters") List<String> facCMeters
	);
}
