package com.example.factory_utility_visualization_be.service.overview.abnormal_signal;

import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UtilityAlertEvaluationServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);
	private final UtilityAlertEvaluationService service = new UtilityAlertEvaluationService(
			Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC));

	@Test
	void temperatureGtThresholdHonorsExclusiveBoundary() {
		F2UtilityAlertMaster rule = threshold("E_Temp", "GT", null, "60");

		assertThat(evaluate("59", "58", List.of(rule), Map.of()).alert()).isFalse();
		assertThat(evaluate("60", "59", List.of(rule), Map.of()).alert()).isFalse();
		assertThat(evaluate("60.1", "60", List.of(rule), Map.of()).alert()).isTrue();
	}

	@Test
	void temperatureJumpUsesAbsoluteDifferenceAndConfiguredThreshold() {
		F2UtilityAlertMaster jump = rule("E_Temp", "JUMP", "GT", "10", null);
		jump.setAggregateType("DIFF");

		assertThat(evaluate("31", "20", List.of(jump), Map.of()).alert()).isTrue();
		assertThat(evaluate("29", "20", List.of(jump), Map.of()).alert()).isFalse();
	}

	@Test
	void temperatureStuckUsesConfiguredWindowRange() {
		F2UtilityAlertMaster stuck = rule("E_Temp", "STUCK", "LTE", "0", 30);
		stuck.setAggregateType("RANGE");

		assertThat(evaluate("20", "20", List.of(stuck),
				Map.of(30, stats("20", "20"))).alert()).isTrue();
		assertThat(evaluate("21", "20", List.of(stuck),
				Map.of(30, stats("20", "21"))).alert()).isFalse();
	}

	@Test
	void temperatureNoDataUsesConfiguredAge() {
		F2UtilityAlertMaster noData = rule("E_Temp", "NO_DATA", null, null, 60);
		UtilitySignalSnapshot old = snapshot("20", "20", NOW.minusMinutes(61), "E_Temp");

		assertThat(service.evaluate(old, List.of(noData), Map.of()).alert()).isTrue();
		assertThat(service.evaluate(snapshot("20", "20", NOW.minusMinutes(60), "E_Temp"),
				List.of(noData), Map.of()).alert()).isFalse();
	}

	@Test
	void humidityDoesNotInheritRulesThatAreAbsentFromMaster() {
		F2UtilityAlertMaster humidityThreshold = threshold("E_Humity", "GT", null, "80");

		UtilitySignalEvaluation result = service.evaluate(
				snapshot("70", "1", NOW.minusHours(4), "E_Humity"),
				List.of(humidityThreshold),
				Map.of(30, stats("70", "70")));

		assertThat(result.alert()).isFalse();
		assertThat(result.ruleType()).isNull();
	}

	@Test
	void ltZeroDoesNotAlertAtZero() {
		F2UtilityAlertMaster rule = threshold("E_Cur1", "LT", "0", null);
		assertThat(evaluate("0", "1", List.of(rule), Map.of()).alert()).isFalse();
		assertThat(evaluate("-0.1", "0", List.of(rule), Map.of()).alert()).isTrue();
	}

	private UtilitySignalEvaluation evaluate(
			String current,
			String previous,
			List<F2UtilityAlertMaster> rules,
			Map<Integer, UtilitySignalWindowStats> stats
	) {
		return service.evaluate(snapshot(current, previous, NOW, rules.get(0).getParameterCode()), rules, stats);
	}

	private UtilitySignalSnapshot snapshot(String current, String previous, LocalDateTime recordedAt, String code) {
		return new UtilitySignalSnapshot("F1", "SCADA", "Electric", 1L, code, "Signal", "unit",
				"device", "D1", recordedAt, decimal(current), decimal(previous));
	}

	private UtilitySignalWindowStats stats(String min, String max) {
		return new UtilitySignalWindowStats(3, decimal(min), decimal(max), null, null);
	}

	private F2UtilityAlertMaster threshold(String code, String operator, String min, String max) {
		F2UtilityAlertMaster rule = rule(code, "THRESHOLD", operator, null, null);
		rule.setMinValue(decimal(min));
		rule.setMaxValue(decimal(max));
		return rule;
	}

	private F2UtilityAlertMaster rule(
			String code, String type, String operator, String threshold, Integer window
	) {
		F2UtilityAlertMaster rule = new F2UtilityAlertMaster();
		rule.setIsActive(true);
		rule.setParameterCode(code);
		rule.setRuleType(type);
		rule.setCompareOperator(operator);
		rule.setThresholdValue(decimal(threshold));
		rule.setWindowMinutes(window);
		rule.setAlertLevel("ALERT");
		rule.setAlertDescription(type + " alert");
		return rule;
	}

	private BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
