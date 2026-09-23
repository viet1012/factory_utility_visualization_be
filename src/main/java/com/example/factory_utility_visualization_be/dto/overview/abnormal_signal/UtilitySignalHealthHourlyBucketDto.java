package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;

import java.time.LocalDateTime;
import java.util.List;

public record UtilitySignalHealthHourlyBucketDto(
		LocalDateTime bucketTime,
		long errorCount,
		List<UtilityAlertLevelSummaryDto> alerts
) {
}
