package com.example.factory_utility_visualization_be.controller;

import com.example.factory_utility_visualization_be.dto.*;
import com.example.factory_utility_visualization_be.request.UtilitySeriesRequest;
import com.example.factory_utility_visualization_be.response.UtilitySeriesResponse;
import com.example.factory_utility_visualization_be.service.UtilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class UtilityController {

    private final UtilityQueryService service;

    // Danh sách nhà máy / scada box
    @GetMapping("/scadas")
    public List<ScadaDto> scadas() {
        return service.getScadas();
    }

    // Danh sách device/channel theo scada
    @GetMapping("/channels")
    public List<ChannelDto> channels(
            @RequestParam(required = false) String facId,
            @RequestParam(required = false) String scadaId,
            @RequestParam(required = false) String cate
    ) {
        return service.getChannels(facId, scadaId, cate);
    }

    // Danh sách parameter master
    @GetMapping("/params")
    public List<ParamDto> params(
            @RequestParam(required = false) String boxDeviceId,
            @RequestParam(required = false) String cate,
            @RequestParam(required = false) String facId
    ) {
        return service.getParams(boxDeviceId, cate, facId);
    }

    // Latest theo device/param (nhiều param một lần)
    @GetMapping("/latest")
    public ResponseEntity<List<LatestRecordDto>> getLatest(
            @RequestParam(required = false) String facId,
            @RequestParam(required = false) String scadaId,
            @RequestParam(required = false) String cate,
            @RequestParam(required = false) String boxDeviceId,
            @RequestParam(required = false, name = "cateIds") String cateIdsCsv
    ) {
        List<String> cateIds = null;
        if (cateIdsCsv != null && !cateIdsCsv.isBlank()) {
            cateIds = java.util.Arrays.stream(cateIdsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        var result = service.getLatest(facId, scadaId, cate, boxDeviceId, cateIds);
        return ResponseEntity.ok(result);
    }


    // Latest cho 1 param
    @GetMapping("/latest/one")
    public LatestRecordDto latestOne(
            @RequestParam String boxDeviceId,
            @RequestParam String plcAddress
    ) {
        return service.getLatestOne(boxDeviceId, plcAddress);
    }

    // Series theo request
    @PostMapping("/series")
    public UtilitySeriesResponse series(@RequestBody UtilitySeriesRequest req) {
        return service.getSeries(req);
    }
}
