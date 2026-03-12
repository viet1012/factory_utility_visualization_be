package com.example.factory_utility_visualization_be.controller.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailyDto;
import com.example.factory_utility_visualization_be.service.overview.daily.UtilityEnergyDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/utility")
public class UtilityOverviewDailyController {
	private final UtilityEnergyDailyService service;

	//	http://localhost:9999/api/utility/energy-daily?facId=Fac_B&month=202603
	@GetMapping("/energy-daily")
	public List<DailyDto> energyDaily(
			@RequestParam String facId,
			@RequestParam String month,
			@RequestParam(required = false) String nameEn

	) {
		return service.getDaily(facId, month, nameEn);
	}
}
