package com.example.factory_utility_visualization_be.controller.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.service.overview.monthly.UtilityEnergyMonthlyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/utility")
public class UtilityOverviewMonthlyController {
	private final UtilityEnergyMonthlyService service;

	//	http://localhost:9999/api/utility/energy-monthly-summary?facId=Fac_B&month=202603
	@GetMapping("/energy-monthly-summary")
	public List<MonthlySummaryDto> getMonthly(
			@RequestParam String facId,
			@RequestParam String month,
			@RequestParam List<String> names
	) {
		return service.getMonthlySummary(facId, month, names);
	}

}
