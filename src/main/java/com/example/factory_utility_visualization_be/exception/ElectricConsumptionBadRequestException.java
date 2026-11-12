package com.example.factory_utility_visualization_be.exception;

/**
 * Invalid input for GET /api/utility/electric-consumption (bad month /
 * months). Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class ElectricConsumptionBadRequestException extends RuntimeException {

	public ElectricConsumptionBadRequestException(String message) {
		super(message);
	}
}
