package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.time.LocalDateTime;

public interface SignalHistoryOldestRawProjection {
	LocalDateTime getRecordedAt();
}
