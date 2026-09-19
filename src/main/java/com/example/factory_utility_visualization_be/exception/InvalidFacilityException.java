package com.example.factory_utility_visualization_be.exception;

/**
 * Thrown when a caller supplies a non-blank facId that is not one of the
 * known facility codes. Mapped to HTTP 400 by GlobalExceptionHandler.
 *
 * Not thrown for a missing/blank facId on an optional parameter — that case
 * keeps each endpoint's existing default/no-filter behavior, which this
 * exception does not change.
 */
public class InvalidFacilityException extends RuntimeException {

	public InvalidFacilityException(String facId) {
		super("Invalid facId: " + facId);
	}
}
