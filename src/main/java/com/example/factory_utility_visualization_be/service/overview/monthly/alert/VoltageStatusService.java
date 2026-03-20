package com.example.factory_utility_visualization_be.service.overview.monthly.alert;

import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageDetailDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageStatusDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.alert.VoltageStatusRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VoltageStatusService {

	private final VoltageStatusRepo repository;

	public VoltageStatusDto getVoltageStatus(String facId) {

		Object[] row = repository.getVoltageStatus().get(0);

		return new VoltageStatusDto(
				(String) row[0],
				((Number) row[1]).doubleValue(),
				((Number) row[2]).doubleValue(),
				(String) row[3],
				OffsetDateTime.now()
		);
	}


		public List<VoltageDetailDto> getVoltageDetail(){

		List<Map<String,Object>> rows = repository.getVoltageDetail();

		return rows.stream().map(r -> new VoltageDetailDto(
				((Timestamp) r.get("recorded_minute")).toLocalDateTime(),
				toDouble(r.get("d12")),
				toDouble(r.get("d14")),
				toDouble(r.get("d16")),
				(String) r.get("alarm"),
				LocalDateTime.now()

		)).toList();
	}

//	public List<VoltageDetailDto> getVoltageDetail1(String facId) {
//
//		List<Map<String, Object>> rows = repository.getVoltageDetail1(facId);
//
//		/// 🔥 GROUP theo thời gian
//		Map<LocalDateTime, Map<String, Double>> grouped = new LinkedHashMap<>();
//		Map<LocalDateTime, String> alarmMap = new HashMap<>();
//
//		for (Map<String, Object> r : rows) {
//
//			LocalDateTime time = ((Timestamp) r.get("recorded_minute")).toLocalDateTime();
//			String name = (String) r.get("name");
//			Double value = toDouble(r.get("value"));
//			String alarm = (String) r.get("alarm");
//
//			grouped.putIfAbsent(time, new HashMap<>());
//			grouped.get(time).put(name, value);
//
//			/// 🔥 nếu có 1 alarm → cả phút alarm
//			if ("Alarm".equals(alarm)) {
//				alarmMap.put(time, "Alarm");
//			} else {
//				alarmMap.putIfAbsent(time, "Normal");
//			}
//		}
//
//		/// 🔥 convert sang DTO
//		List<VoltageDetailDto> result = new ArrayList<>();
//
//		for (var entry : grouped.entrySet()) {
//
//			LocalDateTime time = entry.getKey();
//			Map<String, Double> values = entry.getValue();
//
//			result.add(new VoltageDetailDto1(
//					time,
//					values,
//					alarmMap.getOrDefault(time, "Normal"),
//					LocalDateTime.now()
//			));
//		}
//
//		return result;
//	}

	private Double toDouble(Object value) {
		return value == null ? 0.0 : ((Number) value).doubleValue();
	}
}
