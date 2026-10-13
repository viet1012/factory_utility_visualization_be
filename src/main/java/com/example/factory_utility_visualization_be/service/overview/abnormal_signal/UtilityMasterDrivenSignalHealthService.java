package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.SignalHealthMatrixDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.SignalHealthMatrixItemDto;
import com.example.factory_utility_visualization_be.dto.overview.abnormal_signal.UtilitySignalEvaluation;
import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilityAlertMasterRepository;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilitySignalSnapshotRepository;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalSnapshotProjection;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection.UtilitySignalWindowStatsProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Master-driven signal health flow; backs GET /api/utility/signal-health-matrix. */
@Service
public class UtilityMasterDrivenSignalHealthService {

	private final UtilityAlertMasterRepository alertMasterRepository;
	private final UtilitySignalSnapshotRepository snapshotRepository;
	private final UtilityAlertEvaluationService evaluationService;

	public UtilityMasterDrivenSignalHealthService(
			UtilityAlertMasterRepository alertMasterRepository,
			UtilitySignalSnapshotRepository snapshotRepository,
			UtilityAlertEvaluationService evaluationService
	) {
		this.alertMasterRepository = alertMasterRepository;
		this.snapshotRepository = snapshotRepository;
		this.evaluationService = evaluationService;
	}

	@Transactional(readOnly = true)
	public List<UtilitySignalEvaluation> evaluateSignals() {
		List<F2UtilityAlertMaster> activeRules =
				alertMasterRepository.findByIsActiveTrueOrderByIdAsc();

		Map<String, List<F2UtilityAlertMaster>> rulesByParameter = activeRules.stream()
				.filter(rule -> rule.getParameterCode() != null)
				.collect(Collectors.groupingBy(
						rule -> key(rule.getParameterCode()),
						LinkedHashMap::new,
						Collectors.toList()
				));

		Set<Integer> stuckWindows = activeRules.stream()
				.filter(rule -> "STUCK".equals(key(rule.getRuleType())))
				.map(F2UtilityAlertMaster::getWindowMinutes)
				.filter(minutes -> minutes != null && minutes > 0)
				.collect(Collectors.toSet());

		Map<Integer, Map<Long, UtilitySignalWindowStats>> statsByWindow = new HashMap<>();
		for (Integer window : stuckWindows) {
			Map<Long, UtilitySignalWindowStats> byParaId = snapshotRepository.findWindowStats(window)
					.stream()
					.collect(Collectors.toMap(
							UtilitySignalWindowStatsProjection::getParaId,
							this::toStats
					));
			statsByWindow.put(window, byParaId);
		}

		return snapshotRepository.findLatestSnapshots().stream()
				.map(this::toSnapshot)
				.map(signal -> evaluationService.evaluate(
						signal,
						rulesByParameter.getOrDefault(key(signal.parameterCode()), List.of()),
						statsFor(signal.paraId(), stuckWindows, statsByWindow)
				))
				.toList();
	}

	/** Compatibility adapter for the existing signal-health-matrix JSON shape. */
	public List<SignalHealthMatrixDto> getSignalHealthMatrix() {
		return evaluateSignals().stream()
				.collect(Collectors.groupingBy(
						row -> row.fac() + "|" + row.cate() + "|" + row.scadaId() + "|" + row.boxDeviceId(),
						LinkedHashMap::new,
						Collectors.toList()))
				.values().stream()
				.map(rows -> {
					UtilitySignalEvaluation first = rows.get(0);
					List<SignalHealthMatrixItemDto> signals = rows.stream().map(this::toMatrixItem).toList();
					int alerts = (int) rows.stream().filter(UtilitySignalEvaluation::alert).count();
					return new SignalHealthMatrixDto(first.fac(), first.cate(), first.scadaId(), first.boxDeviceId(),
							signals.size(), alerts, alerts == 0 ? "OK" : "NG", signals);
				})
				.sorted(Comparator.comparing((SignalHealthMatrixDto dto) -> "OK".equals(dto.status()) ? 1 : 0)
						.thenComparing(SignalHealthMatrixDto::fac, Comparator.nullsFirst(String::compareTo))
						.thenComparing(SignalHealthMatrixDto::cate, Comparator.nullsFirst(String::compareTo))
						.thenComparing(SignalHealthMatrixDto::scadaId, Comparator.nullsFirst(String::compareTo))
						.thenComparing(Comparator.comparingInt(SignalHealthMatrixDto::ngRegisters).reversed())
						.thenComparing(SignalHealthMatrixDto::boxDeviceId, Comparator.nullsFirst(String::compareTo)))
				.toList();
	}

	private UtilitySignalSnapshot toSnapshot(UtilitySignalSnapshotProjection row) {
		return new UtilitySignalSnapshot(row.getFac(), row.getScadaId(), row.getCate(), row.getParaId(),
				row.getParameterCode(), row.getSignalName(), row.getUnit(), row.getBoxDeviceId(),
				row.getPlcAddress(), row.getRecordedAt(), row.getCurrentValue(), row.getPrevValue());
	}

	private UtilitySignalWindowStats toStats(UtilitySignalWindowStatsProjection row) {
		return new UtilitySignalWindowStats(row.getSampleCount() == null ? 0 : row.getSampleCount(),
				row.getWindowMinValue(), row.getWindowMaxValue(), row.getWindowAvgValue(), row.getWindowSumValue());
	}

	private Map<Integer, UtilitySignalWindowStats> statsFor(
			Long paraId,
			Set<Integer> windows,
			Map<Integer, Map<Long, UtilitySignalWindowStats>> allStats
	) {
		Map<Integer, UtilitySignalWindowStats> result = new HashMap<>();
		for (Integer window : windows) {
			UtilitySignalWindowStats stats = allStats.getOrDefault(window, Map.of()).get(paraId);
			if (stats != null) {
				result.put(window, stats);
			}
		}
		return result;
	}

	private SignalHealthMatrixItemDto toMatrixItem(UtilitySignalEvaluation row) {
		return new SignalHealthMatrixItemDto(row.signalName(), row.unit(), row.plcAddress(),
				doubleValue(row.currentValue()), doubleValue(row.prevValue()), doubleValue(row.jumpSize()),
				row.status(), row.description(), row.recordedAt());
	}

	private Double doubleValue(Number value) {
		return value == null ? null : value.doubleValue();
	}

	private String key(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}
}
