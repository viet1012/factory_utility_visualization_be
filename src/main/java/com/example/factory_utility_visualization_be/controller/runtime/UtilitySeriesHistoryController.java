package com.example.factory_utility_visualization_be.controller.runtime;

import com.example.factory_utility_visualization_be.dto.runtime.history.SignalHistoryResponseDto;
import com.example.factory_utility_visualization_be.service.runtime.SignalHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilitySeriesHistoryController {

	private final SignalHistoryService service;

	// Chart history: min/avg/max per bucket, with thresholds and signal kind.
	// Params are validated in the service (400 via SignalHistoryBadRequestException),
	// so they are all declared optional here to return one consistent error body.
	@GetMapping("/series/history")
	public ResponseEntity<SignalHistoryResponseDto> seriesHistory(
			@RequestParam(required = false) List<String> boxDeviceId, // repeated or CSV, max 30
			@RequestParam(required = false) String plcAddress,   // CSV: D400,D402
			@RequestParam(required = false) String from,         // ISO-8601, offset optional
			@RequestParam(required = false) String to,
			@RequestParam(required = false, defaultValue = "auto") String bucket // auto|1m|5m|15m|1h|1d
	) {
		return ResponseEntity.ok(
				service.getHistory(boxDeviceId, plcAddress, from, to, bucket)
		);
	}
}
