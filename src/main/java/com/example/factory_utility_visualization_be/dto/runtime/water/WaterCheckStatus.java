package com.example.factory_utility_visualization_be.dto.runtime.water;

/**
 * Status of one water signal in GET /api/utility/water-check.
 * Declaration order is the priority / issue severity order (STALE first).
 * STOPPED / SENSOR_FAULT only apply to pressure signals.
 */
public enum WaterCheckStatus {
	STALE,
	SENSOR_FAULT,
	STOPPED,
	OVER,
	NEAR,
	NO_THRESHOLD,
	OK
}
