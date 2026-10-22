package com.example.factory_utility_visualization_be.dto.runtime.water;

/**
 * What the status of a water signal was evaluated on.
 *   LATEST           the latest raw sample; OVER / NEAR confirmed on the
 *                    last value of the latest 1-minute buckets
 *   MEDIAN_CONFIRMED level signals: the level-median-minutes median; OVER /
 *                    NEAR confirmed on the rolling median of the latest
 *                    1-minute buckets
 */
public enum WaterCheckStatusBasis {
	LATEST,
	MEDIAN_CONFIRMED
}
