package com.example.factory_utility_visualization_be.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Maps InvalidFacilityException to HTTP 400 across all controllers.
 * Scoped to this one exception type only — does not intercept
 * IllegalArgumentException generally, so unrelated existing throw sites
 * (e.g. month parsing, required-field checks) keep their current behavior.
 *
 * SignalHistoryBadRequestException is thrown only by /series/history, so
 * mapping it here does not change any existing endpoint.
 *
 * SolarHourlyBadRequestException is thrown only by /api/solar/hourly (same reason).
 *
 * PowerUsageBadRequestException is thrown only by /power-usage (same reason).
 *
 * WeatherProxyException is thrown only by /weather and carries its own status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidFacilityException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidFacility(InvalidFacilityException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(SignalHistoryBadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleSignalHistoryBadRequest(SignalHistoryBadRequestException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(SolarHourlyBadRequestException.class)
	public ResponseEntity<Map<String, Object>> handleSolarHourlyBadRequest(SolarHourlyBadRequestException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(PowerUsageBadRequestException.class)
	public ResponseEntity<Map<String, Object>> handlePowerUsageBadRequest(PowerUsageBadRequestException ex) {
		return badRequest(ex.getMessage());
	}

	@ExceptionHandler(WeatherProxyException.class)
	public ResponseEntity<Map<String, Object>> handleWeatherProxy(WeatherProxyException ex) {
		return error(ex.getStatus(), ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> badRequest(String message) {
		return error(HttpStatus.BAD_REQUEST, message);
	}

	private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
		return ResponseEntity
				.status(status)
				.body(
						Map.of(
								"status", status.value(),
								"error", status.getReasonPhrase(),
								"message", message,
								"timestamp", OffsetDateTime.now().toString()
						)
				);
	}
}
