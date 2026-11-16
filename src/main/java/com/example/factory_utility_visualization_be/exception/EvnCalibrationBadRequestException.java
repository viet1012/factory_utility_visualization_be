package com.example.factory_utility_visualization_be.exception;

/**
 * Invalid input for POST /api/utility/evn-daily/import and
 * POST /api/utility/loss-calibration/recompute (bad file / params, no
 * usable sample day). Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class EvnCalibrationBadRequestException extends RuntimeException {

	public EvnCalibrationBadRequestException(String message) {
		super(message);
	}
}
