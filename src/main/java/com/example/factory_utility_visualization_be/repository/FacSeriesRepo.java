package com.example.factory_utility_visualization_be.repository;

import com.example.factory_utility_visualization_be.dto.mapper.FacSeriesRow;
import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FacSeriesRepo extends JpaRepository<F2UtilityPara, Long> {

	@Query(value = """
      SELECT
          sc.fac            AS fac,
          ch.cate           AS cate,
          ch.scada_id       AS scadaId,
          hi.box_device_id  AS boxDeviceId,
          hi.plc_address    AS plcAddress,
          pa.name_vi        AS nameVi,
          pa.name_en        AS nameEn,
          pa.unit           AS unit,
          DATEADD(hour, DATEDIFF(hour, 0, hi.pick_at), 0) AS ts,
          MAX(hi.value)     AS value
      FROM F2_Utility_Para_History_Main hi
      JOIN F2_Utility_Para pa
        ON hi.box_device_id = pa.box_device_id
       AND hi.plc_address  = pa.plc_address
      JOIN F2_Utility_Scada_Channel ch
        ON pa.box_device_id = ch.box_device_id
      JOIN F2_Utility_Scada sc
        ON ch.scada_id = sc.scada_id
      WHERE sc.fac = :fac
        AND hi.pick_at >= :from
        AND hi.pick_at <  :to
        AND (:boxDeviceId IS NULL OR :boxDeviceId = '' OR hi.box_device_id = :boxDeviceId)
        AND (:plcAddress IS NULL OR :plcAddress = '' OR hi.plc_address = :plcAddress)
      GROUP BY
          sc.fac, ch.cate, ch.scada_id,
          hi.box_device_id, hi.plc_address,
          pa.name_vi, pa.name_en, pa.unit,
          DATEADD(hour, DATEDIFF(hour, 0, hi.pick_at), 0)
      ORDER BY
          sc.fac, ch.cate, hi.box_device_id, hi.plc_address, ts
      """, nativeQuery = true)
	List<FacSeriesRow> getSeriesHourByFac(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddress") String plcAddress
	);

	@Query(value = """
      SELECT
          sc.fac            AS fac,
          ch.cate           AS cate,
          ch.scada_id       AS scadaId,
          hi.box_device_id  AS boxDeviceId,
          hi.plc_address    AS plcAddress,
          pa.name_vi        AS nameVi,
          pa.name_en        AS nameEn,
          pa.unit           AS unit,
          DATEADD(day, DATEDIFF(day, 0, hi.pick_at), 0) AS ts,
          MAX(hi.value)     AS value
      FROM F2_Utility_Para_History_Main hi
      JOIN F2_Utility_Para pa
        ON hi.box_device_id = pa.box_device_id
       AND hi.plc_address  = pa.plc_address
      JOIN F2_Utility_Scada_Channel ch
        ON pa.box_device_id = ch.box_device_id
      JOIN F2_Utility_Scada sc
        ON ch.scada_id = sc.scada_id
      WHERE sc.fac = :fac
        AND hi.pick_at >= :from
        AND hi.pick_at <  :to
        AND (:boxDeviceId IS NULL OR :boxDeviceId = '' OR hi.box_device_id = :boxDeviceId)
        AND (:plcAddress IS NULL OR :plcAddress = '' OR hi.plc_address = :plcAddress)
      GROUP BY
          sc.fac, ch.cate, ch.scada_id,
          hi.box_device_id, hi.plc_address,
          pa.name_vi, pa.name_en, pa.unit,
          DATEADD(day, DATEDIFF(day, 0, hi.pick_at), 0)
      ORDER BY
          sc.fac, ch.cate, hi.box_device_id, hi.plc_address, ts
      """, nativeQuery = true)
	List<FacSeriesRow> getSeriesDayByFac(
			@Param("fac") String fac,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("boxDeviceId") String boxDeviceId,
			@Param("plcAddress") String plcAddress
	);
}