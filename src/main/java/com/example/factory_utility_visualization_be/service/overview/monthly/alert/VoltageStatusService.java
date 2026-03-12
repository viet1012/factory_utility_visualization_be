package com.example.factory_utility_visualization_be.service.overview.monthly.alert;

import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageDetailDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageStatusDto;
import com.example.factory_utility_visualization_be.repository.monthly.alert.VoltageStatusRepo;
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
	public VoltageStatusDto getVoltageStatus() {

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

	private Double toDouble(Object value){
		if(value == null) return null;
		return ((Number) value).doubleValue();
	}
}
