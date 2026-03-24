package com.example.factory_utility_visualization_be.service.overview.monthly.alert;

import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageDetailDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageStatusDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.alert.VoltageStatusRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VoltageStatusService {

	private final VoltageStatusRepo repository;

	public VoltageStatusDto getVoltageStatus(String facId) {
		List<Object[]> rows = repository.getVoltageStatus(facId);

		if (rows == null || rows.isEmpty()) {
			return new VoltageStatusDto(
					facId,
					null,
					"Voltage",
					0.0,
					0.0,
					"No Data",
					OffsetDateTime.now()
			);
		}

		Object[] row = rows.get(0);

		return new VoltageStatusDto(
				(String) row[0],
				row[1] != null ? String.valueOf(row[1]) : null,
				(String) row[2],
				row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,
				row[4] != null ? ((Number) row[4]).doubleValue() : 0.0,
				(String) row[5],
				OffsetDateTime.now()
		);
	}


	public List<VoltageDetailDto> getVoltageDetail(String facId) {

		List<Map<String, Object>> rows = repository.getVoltageDetail(facId);

		return rows.stream().map(r -> new VoltageDetailDto(
				((Timestamp) r.get("recorded_minute")).toLocalDateTime(),
				toDouble(r.get("D108")),
				toDouble(r.get("D110")),
				toDouble(r.get("D112")),
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
