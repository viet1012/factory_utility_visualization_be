package com.example.factory_utility_visualization_be.controller.overview.electric;

import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class ElectricConsumptionController {

	private final ElectricConsumptionService service;

	// "Elec. Daily Consumption" charts (F2 electric consumption.xlsx):
	// KVH / Fac A&B / Fac C, MWh + K$, monthly per-day average + daily bars.
	//	http://localhost:9999/api/utility/electric-consumption?month=2026-09&months=12
	//	hour stamp check: ...?month=2026-08&months=0&hourOffset=-1 (and 0, +1)
	@GetMapping("/electric-consumption")
	public ResponseEntity<ElectricConsumptionResponseDto> electricConsumption(
			@RequestParam(required = false) String month,   // bill month yyyy-MM, default = current bill month
			@RequestParam(required = false) Integer months,  // previous bill periods in "monthly", default 12
			@RequestParam(required = false) Integer hourOffset // -3..3, default utility.electric-consumption.hour-offset
	) {
		return ResponseEntity.ok(service.getElectricConsumption(month, months, hourOffset));
	}
}
