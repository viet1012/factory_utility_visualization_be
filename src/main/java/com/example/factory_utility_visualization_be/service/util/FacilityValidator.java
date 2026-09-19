package com.example.factory_utility_visualization_be.service.util;

import com.example.factory_utility_visualization_be.exception.InvalidFacilityException;

import java.util.Set;

/**
 * Single source of truth for the known facility codes, replacing the
 * per-service ALLOWED_FACS/normalizeFac duplicates. Canonicalizes case
 * (e.g. "fac_a" -> "Fac_A") and rejects anything else with
 * InvalidFacilityException (mapped to HTTP 400 by GlobalExceptionHandler).
 *
 * Three call shapes, matching the differing contracts already in place
 * across active endpoints — none of them change what "missing/blank" means
 * for their caller, only what happens when a value IS supplied and invalid:
 *
 * - normalizeOptionalWithDefault: blank/null -> DEFAULT_FAC (unchanged
 *   default-substitution behavior); non-blank invalid -> throws.
 *   Used by endpoints that already default an omitted facId to KVH.
 *
 * - normalizeOptionalOrNull: blank/null -> null (unchanged "no filter"
 *   behavior); non-blank invalid -> throws.
 *   Used by endpoints where an omitted facId means "don't filter by fac"
 *   rather than "default to KVH".
 *
 * - normalizeRequired: blank/null -> throws (unchanged "required" behavior,
 *   same message shape as before); non-blank invalid -> throws.
 */
public final class FacilityValidator {

	public static final String DEFAULT_FAC = "KVH";

	private static final Set<String> ALLOWED_FACS =
			Set.of(
					"KVH",
					"Fac_A",
					"Fac_B",
					"Fac_C"
			);

	private FacilityValidator() {
	}

	public static String normalizeOptionalWithDefault(String facId) {
		if (facId == null || facId.isBlank()) {
			return DEFAULT_FAC;
		}

		return canonicalizeOrThrow(facId.trim());
	}

	public static String normalizeOptionalOrNull(String facId) {
		if (facId == null || facId.isBlank()) {
			return null;
		}

		return canonicalizeOrThrow(facId.trim());
	}

	public static String normalizeRequired(String facId, String fieldName) {
		if (facId == null || facId.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}

		return canonicalizeOrThrow(facId.trim());
	}

	private static String canonicalizeOrThrow(String trimmed) {
		for (String allowed : ALLOWED_FACS) {
			if (allowed.equalsIgnoreCase(trimmed)) {
				return allowed;
			}
		}

		throw new InvalidFacilityException(trimmed);
	}
}
