package com.example.factory_utility_visualization_be.controller.overview.abnormal_signal;


import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.FacilityHealthDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.SignalHealthMatrixDto;
import com.example.factory_utility_visualization_be.service.overview.abnormal_signal.UtilitySignalHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilitySignalHealthController {

	private final UtilitySignalHealthService service;

	@GetMapping("/abnormal-signals")
	public List<FacilityHealthDto> getAbnormalSignals() {

		return service.getAbnormalSignals();
	}

	@GetMapping("/signal-health-matrix")
	public ResponseEntity<List<SignalHealthMatrixDto>> getSignalHealthMatrix() {
		return ResponseEntity.ok(service.getSignalHealthMatrix());
	}

	@GetMapping("/abnormal-signals/export")
	public ResponseEntity<byte[]> exportAbnormalSignals() {

		byte[] file = service.exportAbnormalSignalsExcel();

		String fileName = "abnormal-signals-" +
				LocalDateTime.now()
						.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
				+ ".xlsx";

		return ResponseEntity.ok()
				.header(
						HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + fileName + "\""
				)
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
				))
				.body(file);
	}
}