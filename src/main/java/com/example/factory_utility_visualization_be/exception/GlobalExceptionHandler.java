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
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidFacilityException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidFacility(InvalidFacilityException ex) {
		return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(
						Map.of(
								"status", HttpStatus.BAD_REQUEST.value(),
								"error", "Bad Request",
								"message", ex.getMessage(),
								"timestamp", OffsetDateTime.now().toString()
						)
				);
	}
}
