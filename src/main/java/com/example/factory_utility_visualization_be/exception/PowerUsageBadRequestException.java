package com.example.factory_utility_visualization_be.exception;

/**
 * Invalid input for GET /api/utility/power-usage (bad date / period, future
 * date). Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class PowerUsageBadRequestException extends RuntimeException {

	public PowerUsageBadRequestException(String message) {
		super(message);
	}
}
