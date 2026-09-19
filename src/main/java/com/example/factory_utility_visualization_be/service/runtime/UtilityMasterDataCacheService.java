package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.model.F2UtilityScada;
import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaChannelRepo;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilityMasterDataCacheService {

	private static final String ALL_KEY = "'all'";

	private final F2UtilityScadaRepo scadaRepo;
	private final F2UtilityScadaChannelRepo channelRepo;

	@Cacheable(
			cacheNames = UtilityCacheNames.SCADA_MASTER,
			key = ALL_KEY,
			sync = true
	)
	public List<F2UtilityScada> findAllScadas() {
		return scadaRepo.findAll();
	}

	@Cacheable(
			cacheNames = UtilityCacheNames.CHANNEL_MASTER,
			key = ALL_KEY,
			sync = true
	)
	public List<F2UtilityScadaChannel> findAllChannels() {
		return channelRepo.findAll();
	}

	// Callers invoke this only after repository.save()/delete() has already
	// returned successfully, so the write is already durably committed
	// (Spring Data auto-commits each save()/delete() when the caller isn't
	// @Transactional, which is the case today). If a caller is ever wrapped
	// in @Transactional, this eviction must move to run after commit
	// (e.g. TransactionSynchronizationManager or @TransactionalEventListener
	// (phase = AFTER_COMMIT)), otherwise a concurrent read could repopulate
	// the cache with pre-commit data.
	@CacheEvict(
			cacheNames = UtilityCacheNames.SCADA_MASTER,
			allEntries = true
	)
	public void evictScadas() {
	}

	// See evictScadas() note above — same after-commit requirement applies.
	@CacheEvict(
			cacheNames = UtilityCacheNames.CHANNEL_MASTER,
			allEntries = true
	)
	public void evictChannels() {
	}
}
