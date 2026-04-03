package com.example.factory_utility_visualization_be.controller.overview.monthly.alert;

import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageDetailDto;
import com.example.factory_utility_visualization_be.dto.overview.monthly.alert.VoltageStatusDto;
import com.example.factory_utility_visualization_be.service.overview.monthly.alert.VoltageStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/utility")
public class VoltageStatusController {
	private final VoltageStatusService service;

	@GetMapping("/voltage/status")
	public List<VoltageStatusDto> getVoltageStatus(@RequestParam String facId) {
		return service.getVoltageStatus(facId);
	}

	@GetMapping("/voltage/detail")
	public List<VoltageDetailDto> getVoltageDetail(@RequestParam String facId) {
		return service.getVoltageDetail(facId);
	}
}
