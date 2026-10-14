package com.example.factory_utility_visualization_be.exception;

/**
 * Invalid input for GET /api/solar/hourly (bad date format, future date).
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 *
 * Dedicated type so the 400 mapping applies to this endpoint only — other
 * endpoints keep their current IllegalArgumentException/parse behavior.
 */
public class SolarHourlyBadRequestException extends RuntimeException {

	public SolarHourlyBadRequestException(String message) {
		super(message);
	}
}
