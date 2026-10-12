package com.example.factory_utility_visualization_be.exception;

/**
 * Invalid input for GET /api/utility/series/history (missing boxDeviceId,
 * bad date-time format, from >= to, range too long, invalid bucket...).
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 *
 * Dedicated type so the 400 mapping applies to this endpoint only — other
 * endpoints keep their current IllegalArgumentException/parse behavior.
 */
public class SignalHistoryBadRequestException extends RuntimeException {

	public SignalHistoryBadRequestException(String message) {
		super(message);
	}
}
