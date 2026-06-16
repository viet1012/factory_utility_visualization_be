package com.example.factory_utility_visualization_be.controller.overview.command;


import com.example.factory_utility_visualization_be.dto.overview.command.UtilityOverviewKpiDto;
import com.example.factory_utility_visualization_be.service.overview.command.UtilityOverviewKpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/utility/overview")
@RequiredArgsConstructor
public class UtilityOverviewController {

	private final UtilityOverviewKpiService service;

	@GetMapping("/kpi")
	public UtilityOverviewKpiDto getKpi(
			@RequestParam(defaultValue = "Electricity") String cate,
			@RequestParam(defaultValue = "All Factory") String factory,
			@RequestParam(defaultValue = "Today") String period
	) {
		return service.getKpi(cate, factory, period);
	}
}