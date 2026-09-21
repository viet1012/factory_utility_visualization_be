package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UtilityAlertEvaluationService {

	private final Clock clock;

	public UtilityAlertEvaluationService() {
		this(Clock.systemDefaultZone());
	}

	UtilityAlertEvaluationService(Clock clock) {
		this.clock = clock;
	}

	public UtilitySignalEvaluation evaluate(
			UtilitySignalSnapshot signal,
			List<F2UtilityAlertMaster> rules,
			Map<Integer, UtilitySignalWindowStats> windowStats
	) {
		F2UtilityAlertMaster matched = rules.stream()
				.filter(rule -> isTriggered(signal, rule, windowStats))
				.findFirst()
				.orElse(null);

		BigDecimal jumpSize = signal.currentValue() == null || signal.prevValue() == null
				? null
				: signal.currentValue().subtract(signal.prevValue()).abs();

		return new UtilitySignalEvaluation(
				signal.fac(), signal.scadaId(), signal.cate(), signal.paraId(),
				signal.parameterCode(), signal.signalName(), signal.unit(),
				signal.boxDeviceId(), signal.plcAddress(), signal.recordedAt(),
				signal.currentValue(), signal.prevValue(), jumpSize,
				matched != null,
				matched == null ? "OK" : statusOf(matched),
				matched == null ? "Normal" : descriptionOf(matched),
				matched == null ? null : matched.getRuleType(),
				matched == null ? null : matched.getAlertLevel()
		);
	}

	private boolean isTriggered(
			UtilitySignalSnapshot signal,
			F2UtilityAlertMaster rule,
			Map<Integer, UtilitySignalWindowStats> windowStats
	) {
		if (!Boolean.TRUE.equals(rule.getIsActive()) || rule.getRuleType() == null) {
			return false;
		}

		return switch (normalize(rule.getRuleType())) {
			case "THRESHOLD" -> thresholdTriggered(signal.currentValue(), rule);
			case "JUMP" -> jumpTriggered(signal, rule);
			case "STUCK" -> stuckTriggered(rule, windowStats);
			case "NO_DATA" -> noDataTriggered(signal.recordedAt(), rule.getWindowMinutes());
			default -> false;
		};
	}

	private boolean thresholdTriggered(BigDecimal actual, F2UtilityAlertMaster rule) {
		if (actual == null) {
			return false;
		}
		return switch (normalize(rule.getCompareOperator())) {
			case "GT" -> compare(actual, rule.getMaxValue(), value -> value > 0);
			case "GTE" -> compare(actual, rule.getMaxValue(), value -> value >= 0);
			case "LT" -> compare(actual, rule.getMinValue(), value -> value < 0);
			case "LTE" -> compare(actual, rule.getMinValue(), value -> value <= 0);
			case "OUTSIDE_RANGE" -> rule.getMinValue() != null && rule.getMaxValue() != null
					&& (actual.compareTo(rule.getMinValue()) < 0 || actual.compareTo(rule.getMaxValue()) > 0);
			default -> false;
		};
	}

	private boolean jumpTriggered(UtilitySignalSnapshot signal, F2UtilityAlertMaster rule) {
		if (!"DIFF".equals(normalize(rule.getAggregateType()))
				|| signal.currentValue() == null || signal.prevValue() == null) {
			return false;
		}
		BigDecimal diff = signal.currentValue().subtract(signal.prevValue()).abs();
		return compareByOperator(diff, rule.getThresholdValue(), rule.getCompareOperator());
	}

	private boolean stuckTriggered(
			F2UtilityAlertMaster rule,
			Map<Integer, UtilitySignalWindowStats> windowStats
	) {
		if (!"RANGE".equals(normalize(rule.getAggregateType())) || rule.getWindowMinutes() == null) {
			return false;
		}
		UtilitySignalWindowStats stats = windowStats.get(rule.getWindowMinutes());
		if (stats == null || stats.sampleCount() == 0 || stats.minValue() == null || stats.maxValue() == null) {
			return false;
		}
		BigDecimal range = stats.maxValue().subtract(stats.minValue());
		return compareByOperator(range, rule.getThresholdValue(), rule.getCompareOperator());
	}

	private boolean noDataTriggered(LocalDateTime recordedAt, Integer windowMinutes) {
		if (recordedAt == null) {
			return true;
		}
		return windowMinutes != null && windowMinutes > 0
				&& Duration.between(recordedAt, LocalDateTime.now(clock))
				.compareTo(Duration.ofMinutes(windowMinutes)) > 0;
	}

	private boolean compareByOperator(BigDecimal actual, BigDecimal threshold, String operator) {
		return switch (normalize(operator)) {
			case "GT" -> compare(actual, threshold, value -> value > 0);
			case "GTE" -> compare(actual, threshold, value -> value >= 0);
			case "LT" -> compare(actual, threshold, value -> value < 0);
			case "LTE" -> compare(actual, threshold, value -> value <= 0);
			default -> false;
		};
	}

	private boolean compare(BigDecimal actual, BigDecimal expected, IntCondition condition) {
		return expected != null && condition.test(actual.compareTo(expected));
	}

	private String statusOf(F2UtilityAlertMaster rule) {
		return rule.getAlertLevel() == null || rule.getAlertLevel().isBlank()
				? normalize(rule.getRuleType())
				: rule.getAlertLevel();
	}

	private String descriptionOf(F2UtilityAlertMaster rule) {
		return rule.getAlertDescription() == null || rule.getAlertDescription().isBlank()
				? rule.getRuleName()
				: rule.getAlertDescription();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	@FunctionalInterface
	private interface IntCondition {
		boolean test(int value);
	}
}
