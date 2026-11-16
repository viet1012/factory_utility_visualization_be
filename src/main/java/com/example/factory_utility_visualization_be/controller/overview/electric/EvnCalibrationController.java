package com.example.factory_utility_visualization_be.controller.overview.electric;

import com.example.factory_utility_visualization_be.dto.overview.electric.EvnImportResultDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.LossCalibrationResultDto;
import com.example.factory_utility_visualization_be.service.overview.electric.LossCalibrationService;
import com.example.factory_utility_visualization_be.service.overview.electric.evn.EvnDailyImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * EVN main meter (official kWh, F2 electric consumption.xlsx) and the loss
 * calibration of the sub-meter grid. Neither endpoint changes measured data.
 */
@RestController
@RequestMapping("/api/utility")
@RequiredArgsConstructor
public class EvnCalibrationController {

	private final EvnDailyImportService importService;
	private final LossCalibrationService calibrationService;

	//	curl -F "file=@F2 electric consumption.xlsx" "http://localhost:9999/api/utility/evn-daily/import?importedBy=viet&dryRun=true"
	//	one sheet: ...?sheet=FY26-Sep%20%2B%20Solar1   (wrong year in the name: &month=2026-09)
	@PostMapping(value = "/evn-daily/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<EvnImportResultDto> importEvnDaily(
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String sheet,     // exact sheet name, default = every "FY..-<Mon> + Solar1"
			@RequestParam(required = false) String month,     // bill month yyyy-MM overriding the sheet name (needs sheet)
			@RequestParam(required = false) String importedBy,
			@RequestParam(defaultValue = "false") boolean dryRun // true = validate + count only, nothing written
	) {
		return ResponseEntity.ok(importService.importFile(file, sheet, month, importedBy, dryRun));
	}

	//	curl -X POST "http://localhost:9999/api/utility/loss-calibration/recompute?scope=FAC_AB&days=30&createdBy=viet"
	//	(KVH -> 400: calibrated KVH = calibrated FAC_AB + calibrated FAC_C)
	@PostMapping("/loss-calibration/recompute")
	public ResponseEntity<LossCalibrationResultDto> recomputeLossCalibration(
			@RequestParam String scope,                        // FAC_AB | FAC_C

			@RequestParam(required = false) Integer days,      // window length, default 30
			@RequestParam(required = false) String until,      // exclusive window end yyyy-MM-dd, default today
			@RequestParam(required = false) String createdBy
	) {
		return ResponseEntity.ok(calibrationService.recompute(scope, days, until, createdBy));
	}
}
