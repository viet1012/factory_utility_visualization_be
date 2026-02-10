package com.example.factory_utility_visualization_be.controller.test;


import com.example.factory_utility_visualization_be.response.test.DashboardResponse;
import com.example.factory_utility_visualization_be.service.test.UtilityOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/utility")
public class UtilityOverviewController {

    @Autowired
    private UtilityOverviewService service;

    @GetMapping("/overview")
    public DashboardResponse getUtilityOverview(@RequestParam(required = false) List<String> fac) {
        return service.getOverview(fac);
    }

    @GetMapping("/overview/range")
    public ResponseEntity<DashboardResponse> getOverviewInRange(
            @RequestParam(name = "fac", required = false) List<String> facList,
            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,
            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        return ResponseEntity.ok(service.getOverviewInRange(facList, from, to));
    }

}