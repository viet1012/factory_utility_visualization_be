package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.repository.runtime.SignalHistoryRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.SignalHistoryOldestRawProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Earliest recorded_at still present in F2_Utility_Para_History.
 * Raw only keeps ~24h, so anything before this mark must come from the
 * hourly snapshot table. Cached 5 minutes.
 */
@Service
@RequiredArgsConstructor
public class RawHistoryWindowCacheService {

	private static final String ALL_KEY = "'all'";

	private final SignalHistoryRepo historyRepo;

	// empty when the raw table has no rows
	@Cacheable(
			cacheNames = UtilityCacheNames.RAW_HISTORY_OLDEST,
			key = ALL_KEY,
			sync = true
	)
	public Optional<LocalDateTime> findOldestRawRecordedAt() {
		return historyRepo.findOldestRawRecordedAt()
				.map(SignalHistoryOldestRawProjection::getRecordedAt);
	}
}
