package com.example.factory_utility_visualization_be.controller;


import com.example.factory_utility_visualization_be.dto.UtilityOverviewDTO;
import com.example.factory_utility_visualization_be.service.UtilityOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UtilityDataController {
    @Autowired
    private UtilityOverviewService service;

    public UtilityDataController(UtilityOverviewService service) {
        this.service = service;
    }

    @GetMapping("/api/utility/latest")
    public List<UtilityOverviewDTO> getLatestUtilityData() {
        return service.getLatestData();
    }
}
