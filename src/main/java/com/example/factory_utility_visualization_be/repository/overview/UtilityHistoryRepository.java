package com.example.factory_utility_visualization_be.repository.overview;

import com.example.factory_utility_visualization_be.dto.mapper.LatestRecordView;
import com.example.factory_utility_visualization_be.model.F2UtilityParaHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UtilityHistoryRepository extends JpaRepository<F2UtilityParaHistory, Long> {
	@Query(value = """
			WITH x AS (
			    SELECT
			        h.box_device_id AS boxDeviceId,
			        h.plc_address   AS plcAddress,
			        h.value         AS value,
			        h.recorded_at   AS recordedAt,
			
			        p.cate_id       AS cateId,
			        p.name_en       AS nameEn,
			        p.unit          AS unit,
			
			        ch.scada_id     AS scadaId,
			        sc.fac          AS fac,
			        ch.cate         AS cate,
			        ch.box_id       AS boxId,
			
			        ROW_NUMBER() OVER (
			            PARTITION BY sc.fac, h.box_device_id, h.plc_address
			            ORDER BY h.recorded_at DESC, h.id DESC
			        ) AS rn
			    FROM f2_utility_para_history h
			    LEFT JOIN f2_utility_scada_channel ch
			           ON ch.box_device_id = h.box_device_id
			    LEFT JOIN f2_utility_scada sc
			           ON sc.scada_id = ch.scada_id
			    LEFT JOIN f2_utility_para p
			           ON p.box_device_id = h.box_device_id
			          AND p.plc_address   = h.plc_address
			    WHERE
			        (:useFacIds = 0 OR sc.fac IN (:facIds))
			        AND (:useBoxIds = 0 OR h.box_device_id IN (:boxDeviceIds))
			        AND (:usePlc = 0 OR h.plc_address IN (:plcAddresses))
			        AND (:useCateIds = 0 OR p.cate_id IN (:cateIds))
			)
			SELECT *
			FROM x
			WHERE rn = 1
			ORDER BY fac, boxDeviceId, plcAddress
			""", nativeQuery = true)
	List<LatestRecordView> latestPerKeyMulti(
			int useFacIds, List<String> facIds,
			int useBoxIds, List<String> boxDeviceIds,
			int usePlc, List<String> plcAddresses,
			int useCateIds, List<String> cateIds
	);

}
