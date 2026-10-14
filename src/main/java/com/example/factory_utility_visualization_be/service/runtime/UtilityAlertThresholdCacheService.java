package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.model.F2UtilityAlertMaster;
import com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.UtilityAlertMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Active THRESHOLD rules from F2_Utility_Alert_Master, keyed by
 * trim + upper ParameterCode (= f2_utility_para.cate_id).
 *
 * Each side is chosen independently:
 *   low  = MinValue of the LT/LTE/OUTSIDE_RANGE rule with the highest AlertLevel
 *   high = MaxValue of the GT/GTE/OUTSIDE_RANGE rule with the highest AlertLevel
 *   alertLevel = highest AlertLevel among the chosen rules
 * Ties on AlertLevel keep the lowest Id.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityAlertThresholdCacheService {

	private static final String ALL_KEY = "'all'";
	private static final String RULES_KEY = "'rules'";

	private static final Set<String> LOW_OPERATORS = Set.of("LT", "LTE", "OUTSIDE_RANGE");
	private static final Set<String> HIGH_OPERATORS = Set.of("GT", "GTE", "OUTSIDE_RANGE");

	private final UtilityAlertMasterRepository alertMasterRepository;

	public record AlertThreshold(BigDecimal low, BigDecimal high, Integer alertLevel) {
	}

	// One active THRESHOLD rule. low/high follow the same operator mapping as
	// AlertThreshold (low only for LT/LTE/OUTSIDE_RANGE, high only for
	// GT/GTE/OUTSIDE_RANGE); a side the operator does not use is null.
	public record AlertRule(
			Long id, Integer level, BigDecimal low, BigDecimal high, String ruleName, String description) {
	}

	/**
	 * Every active THRESHOLD rule per code, sorted by AlertLevel descending
	 * (unparseable level last), then Id ascending.
	 */
	@Cacheable(
			cacheNames = UtilityCacheNames.ALERT_THRESHOLD,
			key = RULES_KEY,
			sync = true
	)
	public Map<String, List<AlertRule>> findActiveThresholdRulesByCode() {
		Comparator<AlertRule> order = Comparator
				.comparing(AlertRule::level, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(AlertRule::id, Comparator.nullsLast(Comparator.naturalOrder()));

		Map<String, List<AlertRule>> result = alertMasterRepository
				.findByIsActiveTrueOrderByIdAsc()
				.stream()
				.filter(rule -> "THRESHOLD".equals(normalize(rule.getRuleType())))
				.filter(rule -> !normalize(rule.getParameterCode()).isEmpty())
				.collect(Collectors.groupingBy(
						rule -> normalize(rule.getParameterCode()),
						Collectors.mapping(this::toAlertRule, Collectors.toList())
				));

		result.replaceAll((code, rules) -> rules.stream().sorted(order).toList());

		return Map.copyOf(result);
	}

	private AlertRule toAlertRule(F2UtilityAlertMaster rule) {
		String operator = normalize(rule.getCompareOperator());

		return new AlertRule(
				rule.getId(),
				parseLevel(rule.getAlertLevel()),
				LOW_OPERATORS.contains(operator) ? rule.getMinValue() : null,
				HIGH_OPERATORS.contains(operator) ? rule.getMaxValue() : null,
				rule.getRuleName(),
				rule.getAlertDescription()
		);
	}

	@Cacheable(
			cacheNames = UtilityCacheNames.ALERT_THRESHOLD,
			key = ALL_KEY,
			sync = true
	)
	public Map<String, AlertThreshold> findActiveThresholdsByCode() {
		Map<String, List<F2UtilityAlertMaster>> rulesByCode = alertMasterRepository
				.findByIsActiveTrueOrderByIdAsc()
				.stream()
				.filter(rule -> "THRESHOLD".equals(normalize(rule.getRuleType())))
				.filter(rule -> !normalize(rule.getParameterCode()).isEmpty())
				.collect(Collectors.groupingBy(
						rule -> normalize(rule.getParameterCode()),
						LinkedHashMap::new,
						Collectors.toList()
				));

		Map<String, AlertThreshold> result = new HashMap<>();
		List<String> conflicts = new ArrayList<>();

		rulesByCode.forEach((code, rules) -> {
			Optional<F2UtilityAlertMaster> low = chooseSide(
					code, "low", rules, LOW_OPERATORS, F2UtilityAlertMaster::getMinValue, conflicts);
			Optional<F2UtilityAlertMaster> high = chooseSide(
					code, "high", rules, HIGH_OPERATORS, F2UtilityAlertMaster::getMaxValue, conflicts);

			Integer alertLevel = Stream.of(low, high)
					.flatMap(Optional::stream)
					.map(rule -> parseLevel(rule.getAlertLevel()))
					.filter(Objects::nonNull)
					.max(Integer::compare)
					.orElse(null);

			result.put(code, new AlertThreshold(
					low.map(F2UtilityAlertMaster::getMinValue).orElse(null),
					high.map(F2UtilityAlertMaster::getMaxValue).orElse(null),
					alertLevel
			));
		});

		if (!conflicts.isEmpty()) {
			log.warn("Multiple active THRESHOLD rules for the same side; using highest AlertLevel: {}",
					String.join("; ", conflicts));
		}

		return Map.copyOf(result);
	}

	public static String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private Optional<F2UtilityAlertMaster> chooseSide(
			String code,
			String side,
			List<F2UtilityAlertMaster> rules,
			Set<String> operators,
			Function<F2UtilityAlertMaster, BigDecimal> limit,
			List<String> conflicts
	) {
		List<F2UtilityAlertMaster> candidates = rules.stream()
				.filter(rule -> operators.contains(normalize(rule.getCompareOperator())))
				.filter(rule -> limit.apply(rule) != null)
				.toList();

		// Stream.max keeps the first (lowest Id) rule on ties
		Optional<F2UtilityAlertMaster> chosen = candidates.stream()
				.max(Comparator.comparingInt(this::levelRank));

		if (candidates.size() > 1) {
			conflicts.add(code + " " + side + ": ids "
					+ candidates.stream().map(F2UtilityAlertMaster::getId).toList()
					+ " -> id " + chosen.map(F2UtilityAlertMaster::getId).orElse(null));
		}

		return chosen;
	}

	private int levelRank(F2UtilityAlertMaster rule) {
		Integer level = parseLevel(rule.getAlertLevel());
		return level == null ? Integer.MIN_VALUE : level;
	}

	private Integer parseLevel(String alertLevel) {
		if (alertLevel == null || alertLevel.isBlank()) {
			return null;
		}

		try {
			return Integer.valueOf(alertLevel.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
