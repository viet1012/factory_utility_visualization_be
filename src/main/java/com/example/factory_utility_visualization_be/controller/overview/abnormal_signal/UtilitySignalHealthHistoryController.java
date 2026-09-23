package com.example.factory_utility_visualization_be.controller.overview.abnormal_signal;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilitySignalHealthHourlyBucketDto;
import com.example.factory_utility_visualization_be.service.overview.abnormal_signal.UtilitySignalHealthHistoryService;

@RestController
@RequestMapping("/api/utility/signal-health/history")
public class UtilitySignalHealthHistoryController {

	private final UtilitySignalHealthHistoryService historyService;

	public UtilitySignalHealthHistoryController(UtilitySignalHealthHistoryService historyService) {
		this.historyService = historyService;
	}

	@GetMapping("/hourly")
	public ResponseEntity<List<UtilitySignalHealthHourlyBucketDto>> getHourly(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(required = false) String fac,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) String status
	) {
		return ResponseEntity.ok(historyService.getHourly(
				from, to, fac, cate, scadaId, boxDeviceId, status));
	}
}
