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

	public List<VoltageStatusDto> getVoltageStatus(String facId) {
		List<Object[]> rows = repository.getVoltageStatus(facId);

		if (rows == null || rows.isEmpty()) {
			return List.of(
					new VoltageStatusDto(
							facId,
							null,
							"Voltage",
							0.0,
							0.0,
							0.0,
							0.0,
							"No Data",
							OffsetDateTime.now()
					)
			);
		}

		return rows.stream()
				.map(row -> new VoltageStatusDto(
						facId,
						row[1] != null ? String.valueOf(row[1]) : null,               // boxDeviceId
						row[0] != null ? String.valueOf(row[0]) : null,               // cateId
						row[2] != null ? ((Number) row[2]).doubleValue() : 0.0,       // minVol
						row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,       // maxVol
						row[4] != null ? ((Number) row[4]).doubleValue() : 0.0,       // minVolStd
						row[5] != null ? ((Number) row[5]).doubleValue() : 0.0,       // maxVolStd
						row[6] != null ? String.valueOf(row[6]) : "Normal",           // alarm
						OffsetDateTime.now()
				))
				.toList();
	}

	public List<VoltageDetailDto> getVoltageDetail(String facId) {
		List<Map<String, Object>> rows = repository.getVoltageDetail(facId);

		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		return rows.stream()
				.map(r -> new VoltageDetailDto(
						toLocalDateTime(r.get("recorded_minute")),
						r.get("cate_id") != null ? String.valueOf(r.get("cate_id")) : null,
						r.get("box_device_id") != null ? String.valueOf(r.get("box_device_id")) : null,
						toDouble(r.get("min_vol")),
						toDouble(r.get("max_vol")),
						toDouble(r.get("min_vol_std")),
						toDouble(r.get("max_vol_std")),
						r.get("alarm") != null ? String.valueOf(r.get("alarm")) : "Normal",
						LocalDateTime.now()
				))
				.toList();
	}

	private Double toDouble(Object value) {
		return value == null ? 0.0 : ((Number) value).doubleValue();
	}
	private LocalDateTime toLocalDateTime(Object value) {
		if (value == null) return null;

		if (value instanceof Timestamp ts) {
			return ts.toLocalDateTime();
		}

		if (value instanceof LocalDateTime ldt) {
			return ldt;
		}

		throw new IllegalArgumentException("Unsupported datetime type: " + value.getClass());
	}
}
