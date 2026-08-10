package com.example.factory_utility_visualization_be.repository.overview.solar;

import com.example.factory_utility_visualization_be.dto.overview.solar.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.model.DummyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SolarDashboardRepo
		extends JpaRepository<DummyEntity, Long> {

	@Query(value = """
			WITH DeviceMap AS (
			    SELECT DISTINCT
			        ch.box_device_id,
			        sc.fac,

			        CASE
			            WHEN UPPER(
			                LTRIM(
			                    RTRIM(
			                        ISNULL(ch.box_id, '')
			                    )
			                )
			            ) = 'SOLAR'
			            THEN 1
			            ELSE 0
			        END AS is_solar

			    FROM dbo.F2_Utility_Scada_Channel ch

			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id
			),

			EnergyRaw AS (
			    SELECT
			        dm.is_solar,

			        CAST(
			            hm.[value]
			            AS DECIMAL(19, 6)
			        ) AS energy_value

			    FROM dbo.F2_Utility_Para_History_Main hm

			    INNER JOIN dbo.F2_Utility_Para pa
			        ON pa.box_device_id = hm.box_device_id
			       AND pa.plc_address = hm.plc_address

			    INNER JOIN DeviceMap dm
			        ON dm.box_device_id = hm.box_device_id

			    WHERE
			        pa.name_en = :energyName

			        AND hm.pick_at >= :todayStart
			        AND hm.pick_at < :tomorrowStart

			        AND hm.[value] > 0

			        AND ISNULL(
			            hm.MTD,
			            ''
			        ) = 'MTD'

			        AND (
			            UPPER(:fac) = 'KVH'
			            OR UPPER(dm.fac) = UPPER(:fac)
			        )
			),

			TodayEnergy AS (
			    SELECT
			        SUM(
			            CASE
			                WHEN is_solar = 0
			                    THEN energy_value
			                ELSE 0
			            END
			        ) AS gross_value,

			        SUM(
			            CASE
			                WHEN is_solar = 1
			                    THEN energy_value
			                ELSE 0
			            END
			        ) AS solar_value

			    FROM EnergyRaw
			),

			SolarPowerDevices AS (
			    SELECT DISTINCT
			        ch.box_device_id

			    FROM dbo.F2_Utility_Scada_Channel ch

			    INNER JOIN dbo.F2_Utility_Scada sc
			        ON sc.scada_id = ch.scada_id

			    WHERE
			        UPPER(
			            LTRIM(
			                RTRIM(
			                    ISNULL(ch.box_id, '')
			                )
			            )
			        ) = 'SOLAR'

			        AND (
			            UPPER(:fac) = 'KVH'
			            OR UPPER(sc.fac) = UPPER(:fac)
			        )
			),

			SolarPowerParams AS (
			    SELECT
			        pa.box_device_id,
			        pa.plc_address

			    FROM dbo.F2_Utility_Para pa

			    INNER JOIN SolarPowerDevices sd
			        ON sd.box_device_id = pa.box_device_id

			    WHERE
			        pa.name_en = :powerName
			),

			LatestPowerRanked AS (
			    SELECT
			        hi.box_device_id,
			        hi.plc_address,

			        CAST(
			            hi.[value]
			            AS DECIMAL(19, 6)
			        ) AS power_value,

			        ROW_NUMBER() OVER (
			            PARTITION BY
			                hi.box_device_id,
			                hi.plc_address

			            ORDER BY
			                hi.recorded_at DESC
			        ) AS row_num

			    FROM dbo.F2_Utility_Para_History hi

			    INNER JOIN SolarPowerParams sp
			        ON sp.box_device_id = hi.box_device_id
			       AND sp.plc_address = hi.plc_address

			    WHERE
			        hi.recorded_at < :now
			        AND hi.[value] >= 0
			),

			CurrentPower AS (
			    SELECT
			        SUM(
			            power_value
			        ) AS current_power

			    FROM LatestPowerRanked

			    WHERE row_num = 1
			)

			SELECT
			    CAST(
			        COALESCE(
			            (
			                SELECT current_power
			                FROM CurrentPower
			            ),
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS currentPowerKw,

			    CAST(
			        COALESCE(
			            te.solar_value,
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS solarKwh,

			    CAST(
			        CASE
			            WHEN COALESCE(
			                te.gross_value,
			                0
			            )
			            >=
			            COALESCE(
			                te.solar_value,
			                0
			            )

			            THEN
			                COALESCE(
			                    te.gross_value,
			                    0
			                )
			                -
			                COALESCE(
			                    te.solar_value,
			                    0
			                )

			            ELSE 0
			        END
			        AS DECIMAL(19, 1)
			    ) AS gridKwh,

			    CAST(
			        COALESCE(
			            te.gross_value,
			            0
			        )
			        AS DECIMAL(19, 1)
			    ) AS totalKwh,

			    CAST(
			        CASE
			            WHEN COALESCE(
			                te.gross_value,
			                0
			            ) > 0

			            THEN
			                COALESCE(
			                    te.solar_value,
			                    0
			                )
			                /
			                NULLIF(
			                    te.gross_value,
			                    0
			                )
			                * 100

			            ELSE 0
			        END
			        AS DECIMAL(10, 1)
			    ) AS solarSharePercent

			FROM TodayEnergy te
			""", nativeQuery = true)
	SolarDashboardProjection getSolarDashboard(
			@Param("fac")
			String fac,

			@Param("todayStart")
			LocalDateTime todayStart,

			@Param("tomorrowStart")
			LocalDateTime tomorrowStart,

			@Param("now")
			LocalDateTime now,

			@Param("powerName")
			String powerName,

			@Param("energyName")
			String energyName
	);
}