package com.example.factory_utility_visualization_be.controller.overview.hourly;

import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyTempCompareDto;
import com.example.factory_utility_visualization_be.dto.overview.hourly.UtilityHourlyDashboardDto;
import com.example.factory_utility_visualization_be.service.overview.hourly.UtilityHourlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/utility")
public class UtilityOverviewHourlyController {

	private final UtilityHourlyService service;

	@GetMapping("/hourly-dashboard")
	public ResponseEntity<UtilityHourlyDashboardDto>
	getHourlyDashboard(
			@RequestParam(required = false)
			String facId,

			@RequestParam(required = false)
			String nameEn,

			@RequestParam(required = false)
			BigDecimal exchange,

			@RequestParam(required = false)
			BigDecimal sepzone
	) {
		return ResponseEntity.ok(
				service.getHourlyDashboard(
						facId,
						nameEn,
						exchange,
						sepzone
				)
		);
	}

	// VD: 	http://localhost:9999/api/utility/energy-hourly?facId=Fac_B&hours=48
	@GetMapping("/energy/hourly")
	public List<HourlyCompareDto> energyHourly(
			@RequestParam(defaultValue = "KVH") String facId,
			@RequestParam(defaultValue = "48") int hours,
			@RequestParam(required = false) String nameEn,
			@RequestParam(defaultValue = "26005") BigDecimal exchange,
			@RequestParam(defaultValue = "1.075") BigDecimal sepzone
	) {
		return
				service.getHourly(facId, hours, nameEn, exchange, sepzone);
	}

	@GetMapping("/hourly-sensor-compare")
	public List<HourlyTempCompareDto> getUtilityHourlySensorCompare(
			@RequestParam(required = false) String facId,
			@RequestParam(required = false, defaultValue = "WATER") String type
	) {
		return service.getUtilityHourlySensorCompare(facId, type);
	}
}