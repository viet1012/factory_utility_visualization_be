package com.example.factory_utility_visualization_be.controller;

import com.example.factory_utility_visualization_be.dto.*;
import com.example.factory_utility_visualization_be.request.UtilitySeriesRequest;
import com.example.factory_utility_visualization_be.response.UtilitySeriesResponse;
import com.example.factory_utility_visualization_be.service.UtilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityController {

	private final UtilityQueryService service;

	// Danh sách nhà máy / scada box
	@GetMapping("/scadas")
	public List<ScadaDto> scadas() {
		return service.getScadas();
	}

	// Danh sách device/channel theo scada
	@GetMapping("/channels")
	public List<ChannelDto> channels(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate
	) {
		return service.getChannels(facId, scadaId, cate);
	}

	// Danh sách parameter master
	@GetMapping("/params")
	public List<ParamDto> params(
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) Integer importantOnly // ✅ NEW
	) {
		return service.getParams(boxDeviceId, cate, facId, importantOnly);
	}

	// Latest theo device/param (nhiều param một lần)
	@GetMapping("/latest")
	public ResponseEntity<List<LatestRecordDto>> getLatest(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false, name = "cateIds") String cateIdsCsv
	) {
		List<String> cateIds = null;
		if (cateIdsCsv != null && !cateIdsCsv.isBlank()) {
			cateIds = java.util.Arrays.stream(cateIdsCsv.split(","))
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.toList();
		}

		var result = service.getLatest(facId, scadaId, cate, boxDeviceId, cateIds);
		return ResponseEntity.ok(result);
	}


	// Latest cho 1 param
	@GetMapping("/latest/one")
	public LatestRecordDto latestOne(
			@RequestParam String boxDeviceId,
			@RequestParam String plcAddress
	) {
		return service.getLatestOne(boxDeviceId, plcAddress);
	}

	@GetMapping("/series/minute")
	public ResponseEntity<List<MinutePointDto>> seriesByMinute(
			@RequestParam String from,   // ISO: 2026-02-10T08:00:00
			@RequestParam String to,

			@RequestParam(required = false) String facId,   // ✅ NEW
			@RequestParam(required = false) String cate,    // ✅ NEW

			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) String plcAddress,
			@RequestParam(required = false, name = "cateIds") String cateIdsCsv
	) {
		var fromTs = LocalDateTime.parse(from);
		var toTs = LocalDateTime.parse(to);

		List<String> cateIds = null;
		if (cateIdsCsv != null && !cateIdsCsv.isBlank()) {
			cateIds = Arrays.stream(cateIdsCsv.split(","))
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.toList();
		}

		// ✅ gọi service version mới có facId + cate
		var rows = service.getSeriesByMinute(
				fromTs,
				toTs,
				facId,
				cate,
				boxDeviceId,
				plcAddress,
				cateIds
		);

		// map view -> dto
		var dto = rows.stream()
				.map(r -> new MinutePointDto(
						r.getTs(),
						r.getValue(),
						r.getBoxDeviceId(),
						r.getPlcAddress(),
						r.getCateId(),
						r.getNameEn(),
						r.getNameVi(),   // optional
						r.getUnit(),     // ✅ NEW
						r.getFac(),
						r.getCate()
				))
				.toList();

		return ResponseEntity.ok(dto);
	}


	// Series theo request
	@PostMapping("/series")
	public UtilitySeriesResponse series(@RequestBody UtilitySeriesRequest req) {
		return service.getSeries(req);
	}

	@GetMapping("/sum-compare")
	public List<SumCompareDto> sumCompare(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) List<String> deviceIds,
			@RequestParam(required = false) List<String> cateIds,

			// ✅ NEW
			@RequestParam(required = false) List<String> nameEns
	) {
		return service.sumCompareByCateAndNameEn(
				facId,
				scadaId,
				cate,
				boxDeviceId,
				deviceIds,
				cateIds,
				nameEns
		);
	}

	@GetMapping("/series/hourly")
	public List<HourPointDto> seriesHourly(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
			LocalDateTime fromTs,

			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
			LocalDateTime toTs,

			@RequestParam(required = false) String fac,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,

			@RequestParam(required = false) String boxDeviceId,

			// ✅ bạn muốn truyền vào plcAddress + cateId/cateIds
			@RequestParam(required = false) String plcAddress,
			@RequestParam(required = false) String cateId,
			@RequestParam(required = false) List<String> cateIds
	) {
		// default: today 00:00 -> tomorrow 00:00
		if (fromTs == null || toTs == null) {
			LocalDate today = LocalDate.now();
			fromTs = today.atStartOfDay();
			toTs = today.plusDays(1).atStartOfDay();
		}

		return service.seriesHourlySum(
				fromTs, toTs,
				fac, scadaId, cate,
				boxDeviceId, plcAddress,
				cateId, cateIds
		);
	}
}
