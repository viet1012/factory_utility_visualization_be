package com.example.factory_utility_visualization_be.dto.runtime.water;

/**
 * Status of one water signal in GET /api/utility/water-check.
 * Declaration order is the issue severity order (OVER first).
 */
public enum WaterCheckStatus {
	OVER,
	NEAR,
	STALE,
	NO_THRESHOLD,
	OK
}
