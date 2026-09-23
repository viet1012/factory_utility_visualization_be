package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilityAlertDescriptionCountDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilityAlertDeviceCountDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilityAlertLevelSummaryDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilityAlertSignalDetailDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilitySignalHealthHourlyBucketDto;
import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilityAlertMasterRepository;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilitySignalHealthHistoryRepository;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalHistoricalWindowStatsProjection;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalHealthHistoryProjection;

@Service
public class UtilitySignalHealthHistoryService {
	private static final List<String> REQUIRED_ALERT_LEVELS = List.of("1", "2", "3");

	private final UtilitySignalHealthHistoryRepository historyRepository;
	private final UtilityAlertMasterRepository alertMasterRepository;
	private final UtilityAlertEvaluationService evaluationService;

	public UtilitySignalHealthHistoryService(
			UtilitySignalHealthHistoryRepository historyRepository,
			UtilityAlertMasterRepository alertMasterRepository,
			UtilityAlertEvaluationService evaluationService
	) {
		this.historyRepository = historyRepository;
		this.alertMasterRepository = alertMasterRepository;
		this.evaluationService = evaluationService;
	}

	@Transactional(readOnly = true)
	public List<UtilitySignalHealthHourlyBucketDto> getHourly(
			LocalDateTime from,
			LocalDateTime to,
			String fac,
			String cate,
			String scadaId,
			String boxDeviceId,
			String status
	) {
		if (from == null || to == null || !from.isBefore(to)) {
			throw new IllegalArgumentException("from must be before to");
		}
		if (Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0) {
			throw new IllegalArgumentException("Hourly history range must not exceed 7 days");
		}

		String normalizedFac = blankToNull(fac);
		String normalizedCate = blankToNull(cate);
		String normalizedScadaId = blankToNull(scadaId);
		String normalizedBoxDeviceId = blankToNull(boxDeviceId);
		String normalizedStatus = blankToNull(status);

		List<F2UtilityAlertMaster> activeRules = alertMasterRepository.findByIsActiveTrueOrderByIdAsc();
		Map<String, List<F2UtilityAlertMaster>> rulesByParameter = activeRules.stream()
				.filter(rule -> rule.getParameterCode() != null)
				.collect(Collectors.groupingBy(
						rule -> key(rule.getParameterCode()),
						LinkedHashMap::new,
						Collectors.toList()
				));

		List<UtilitySignalHealthHistoryProjection> snapshots =
				historyRepository.findExpectedHourlySnapshots(
						from, to, normalizedFac, normalizedCate, normalizedScadaId, normalizedBoxDeviceId);

		List<Integer> activeStuckWindows = activeRules.stream()
				.filter(this::isStuckRule)
				.map(F2UtilityAlertMaster::getWindowMinutes)
				.filter(Objects::nonNull)
				.filter(minutes -> minutes > 0)
				.distinct()
				.toList();

		Map<WindowStatsKey, UtilitySignalWindowStats> historicalWindowStats = new LinkedHashMap<>();
		for (Integer windowMinutes : activeStuckWindows) {
			for (UtilitySignalHistoricalWindowStatsProjection row :
					historyRepository.findHistoricalWindowStats(
							from, to, normalizedFac, normalizedCate, normalizedScadaId,
							normalizedBoxDeviceId, windowMinutes)) {
				historicalWindowStats.put(
						new WindowStatsKey(
								row.getBoxDeviceId(), row.getPlcAddress(),
								row.getBucketAt(), row.getWindowMinutes()),
						toWindowStats(row)
				);
			}
		}

		Map<LocalDateTime, List<UtilitySignalHealthHistoryProjection>> byBucket = snapshots.stream()
				.collect(Collectors.groupingBy(
						UtilitySignalHealthHistoryProjection::getPickAt,
						LinkedHashMap::new,
						Collectors.toList()
				));

		return byBucket.entrySet().stream()
				.map(entry -> summarizeBucket(
						entry.getKey(), entry.getValue(), rulesByParameter,
						historicalWindowStats, normalizedStatus))
				.toList();
	}

	private UtilitySignalHealthHourlyBucketDto summarizeBucket(
			LocalDateTime bucketTime,
			List<UtilitySignalHealthHistoryProjection> snapshots,
			Map<String, List<F2UtilityAlertMaster>> rulesByParameter,
			Map<WindowStatsKey, UtilitySignalWindowStats> historicalWindowStats,
			String statusFilter
	) {
		List<UtilitySignalEvaluation> evaluations = snapshots.stream()
				.filter(row -> row.getParaId() != null)
				.map(row -> evaluate(row, rulesByParameter, historicalWindowStats))
				.filter(UtilitySignalEvaluation::alert)
				.filter(result -> statusFilter == null || statusFilter.equalsIgnoreCase(result.status()))
				.toList();

		List<UtilityAlertLevelSummaryDto> alerts = buildAlertSummaries(evaluations);
		long errorCount = alerts.stream().mapToLong(UtilityAlertLevelSummaryDto::count).sum();

		return new UtilitySignalHealthHourlyBucketDto(
				bucketTime,
				errorCount,
				alerts
		);
	}

	private List<UtilityAlertLevelSummaryDto> buildAlertSummaries(
			List<UtilitySignalEvaluation> evaluations
	) {
		Map<String, List<UtilitySignalEvaluation>> byLevel = evaluations.stream()
				.filter(UtilitySignalEvaluation::alert)
				.collect(Collectors.groupingBy(
						this::alertLevel,
						LinkedHashMap::new,
						Collectors.toList()
				));

		List<String> levels = new java.util.ArrayList<>(REQUIRED_ALERT_LEVELS);
		byLevel.keySet().stream()
				.filter(level -> !REQUIRED_ALERT_LEVELS.contains(level))
				.sorted()
				.forEach(levels::add);

		return levels.stream()
				.map(level -> toLevelSummary(level, byLevel.getOrDefault(level, List.of())))
				.toList();
	}

	private UtilityAlertLevelSummaryDto toLevelSummary(
			String level,
			List<UtilitySignalEvaluation> evaluations
	) {
		Map<DescriptionKey, List<UtilitySignalEvaluation>> byDescription = evaluations.stream()
				.collect(Collectors.groupingBy(
						evaluation -> new DescriptionKey(
								evaluation.parameterCode(),
								evaluation.ruleType(),
								evaluation.description()),
						LinkedHashMap::new,
						Collectors.toList()
				));

		List<UtilityAlertDescriptionCountDto> descriptions = byDescription.entrySet().stream()
				.map(entry -> toDescriptionSummary(entry.getKey(), entry.getValue()))
				.toList();
		long count = descriptions.stream().mapToLong(UtilityAlertDescriptionCountDto::count).sum();
		return new UtilityAlertLevelSummaryDto(level, count, descriptions);
	}

	private UtilityAlertDescriptionCountDto toDescriptionSummary(
			DescriptionKey key,
			List<UtilitySignalEvaluation> evaluations
	) {
		List<UtilityAlertDeviceCountDto> devices = evaluations.stream()
				.collect(Collectors.groupingBy(
						evaluation -> new DeviceKey(evaluation.boxDeviceId()),
						LinkedHashMap::new,
						Collectors.toList()
				))
				.entrySet().stream()
				.map(entry -> toDeviceSummary(entry.getKey(), entry.getValue()))
				.toList();
		long count = devices.stream().mapToLong(UtilityAlertDeviceCountDto::count).sum();

		return new UtilityAlertDescriptionCountDto(
				key.parameterCode(), key.ruleType(), key.description(), count, devices);
	}

	private UtilityAlertDeviceCountDto toDeviceSummary(
			DeviceKey key,
			List<UtilitySignalEvaluation> evaluations
	) {
		List<UtilityAlertSignalDetailDto> signals = evaluations.stream()
				.collect(Collectors.groupingBy(
						evaluation -> new SignalDetailKey(
								evaluation.plcAddress(), evaluation.signalName()),
						LinkedHashMap::new,
						Collectors.toList()
				))
				.values().stream()
				.map(this::toSignalDetail)
				.toList();
		long count = signals.stream().mapToLong(UtilityAlertSignalDetailDto::count).sum();
		return new UtilityAlertDeviceCountDto(key.boxDeviceId(), count, signals);
	}

	private UtilityAlertSignalDetailDto toSignalDetail(List<UtilitySignalEvaluation> evaluations) {
		UtilitySignalEvaluation first = evaluations.get(0);
		return new UtilityAlertSignalDetailDto(
				first.plcAddress(),
				first.signalName(),
				first.currentValue(),
				first.prevValue(),
				first.recordedAt(),
				evaluations.size()
		);
	}

	private String alertLevel(UtilitySignalEvaluation evaluation) {
		return evaluation.alertLevel() == null || evaluation.alertLevel().isBlank()
				? evaluation.status()
				: evaluation.alertLevel();
	}

	private UtilitySignalEvaluation evaluate(
			UtilitySignalHealthHistoryProjection row,
			Map<String, List<F2UtilityAlertMaster>> rulesByParameter,
			Map<WindowStatsKey, UtilitySignalWindowStats> historicalWindowStats
	) {
		List<F2UtilityAlertMaster> rules =
				rulesByParameter.getOrDefault(key(row.getParameterCode()), List.of());
		UtilitySignalSnapshot snapshot = new UtilitySignalSnapshot(
				row.getFac(), row.getScadaId(), row.getCate(), row.getParaId(),
				row.getParameterCode(), row.getSignalName(), row.getUnit(),
				row.getBoxDeviceId(), row.getPlcAddress(), row.getCurrentRecordedAt(),
				row.getCurrentValue(), row.getPrevValue()
		);

		Map<Integer, UtilitySignalWindowStats> windowStats = new LinkedHashMap<>();
		for (F2UtilityAlertMaster rule : rules) {
			Integer windowMinutes = rule.getWindowMinutes();
			if (!isStuckRule(rule) || windowMinutes == null || windowMinutes <= 0) {
				continue;
			}
			UtilitySignalWindowStats stats = historicalWindowStats.get(new WindowStatsKey(
					row.getBoxDeviceId(), row.getPlcAddress(), row.getPickAt(), windowMinutes));
			if (stats != null) {
				windowStats.put(windowMinutes, stats);
			}
		}

		return evaluationService.evaluate(snapshot, rules, windowStats, row.getPickAt());
	}

	private UtilitySignalWindowStats toWindowStats(UtilitySignalHistoricalWindowStatsProjection row) {
		return new UtilitySignalWindowStats(
				row.getSampleCount() == null ? 0 : row.getSampleCount(),
				row.getMinValue(), row.getMaxValue(), row.getAvgValue(), row.getSumValue());
	}

	private boolean isStuckRule(F2UtilityAlertMaster rule) {
		return "STUCK".equals(key(rule.getRuleType()));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String key(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private record DescriptionKey(String parameterCode, String ruleType, String description) {
	}

	private record DeviceKey(String boxDeviceId) {
	}

	private record SignalDetailKey(String plcAddress, String signalName) {
	}

	private record WindowStatsKey(
			String boxDeviceId,
			String plcAddress,
			LocalDateTime bucketAt,
			Integer windowMinutes
	) {
	}
}
