package com.example.factory_utility_visualization_be.controller.runtime;

import com.example.factory_utility_visualization_be.dto.runtime.MinutePointDto;
import com.example.factory_utility_visualization_be.dto.latest.LatestFacilityDto;
import com.example.factory_utility_visualization_be.service.runtime.UtilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityController {

	private final UtilityQueryService service;

	// Latest theo device/param (nhiều param một lần)

	@GetMapping("/latest")
	public ResponseEntity<List<LatestFacilityDto>> getLatest(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false) String scadaId,
			@RequestParam(required = false) String cate,
			@RequestParam(required = false) String boxDeviceId,
			@RequestParam(required = false) List<String> cateIds
	) {
		return ResponseEntity.ok(
				service.getLatest(
						facId,
						scadaId,
						cate,
						boxDeviceId,
						cateIds
				)
		);
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
					.map(value -> value.trim())
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
						r.getSampleRecordedAt(),
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
}
