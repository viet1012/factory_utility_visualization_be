package com.example.factory_utility_visualization_be.controller.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyUtilityUsageDto;
import com.example.factory_utility_visualization_be.service.overview.monthly.MonthlyUtilityUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class MonthlyUtilityUsageController {

	private final MonthlyUtilityUsageService service;

	@GetMapping("/monthly-usage")
	public List<MonthlyUtilityUsageDto> getMonthlyUsage(
			@RequestParam(defaultValue = "Fac_A") String fac,
			@RequestParam int year,
			@RequestParam int month,
			@RequestParam(defaultValue = "Total Energy Consumption") String nameEn
	) {
		return service.getMonthlyUsage(fac, year, month, nameEn);
	}
}