package com.example.factory_utility_visualization_be.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown only by /weather. Carries the HTTP status to return and a short,
 * client-safe message (never the upstream URL, which contains the API key).
 */
public class WeatherProxyException extends RuntimeException {

	private final HttpStatus status;

	public WeatherProxyException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
