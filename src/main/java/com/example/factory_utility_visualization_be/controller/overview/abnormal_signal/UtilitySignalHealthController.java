package com.example.factory_utility_visualization_be.controller.overview.abnormal_signal;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.SignalHealthMatrixDto;
import com.example.factory_utility_visualization_be.service.overview.abnormal_signal.UtilityMasterDrivenSignalHealthService;
import com.example.factory_utility_visualization_be.service.overview.abnormal_signal.UtilitySignalHealthExcelService;

@RestController
@RequestMapping("/api/utility")
public class UtilitySignalHealthController {

    // Legacy: retained for Excel export
//     private final UtilitySignalHealthService legacyService;

    // New master-driven alert engine
    private final UtilityMasterDrivenSignalHealthService masterDrivenService;
    private final UtilitySignalHealthExcelService excelService;

    public UtilitySignalHealthController(
            UtilityMasterDrivenSignalHealthService masterDrivenService,
            UtilitySignalHealthExcelService excelService
    ) {
        this.masterDrivenService = masterDrivenService;
        this.excelService = excelService;
    }

    @GetMapping("/signal-health-matrix")
    public ResponseEntity<List<SignalHealthMatrixDto>> getSignalHealthMatrix() {
        return ResponseEntity.ok(
                masterDrivenService.getSignalHealthMatrix()
        );
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSignalHealth() {
        byte[] file = excelService.export();
        String fileName = "utility-signal-health-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

//     @GetMapping("/abnormal-signals/export")
//     public ResponseEntity<byte[]> exportAbnormalSignals() {
//         byte[] file = legacyService.exportAbnormalSignalsExcel();

//         String fileName = "abnormal-signals-" +
//                 LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
//                 ".xlsx";

//         return ResponseEntity.ok()
//                 .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
//                 .contentType(MediaType.parseMediaType(
//                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
//                 .body(file);
//     }
}
