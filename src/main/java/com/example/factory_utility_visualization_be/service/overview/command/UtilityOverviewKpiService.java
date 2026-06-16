package com.example.factory_utility_visualization_be.service.overview.command;


import com.example.factory_utility_visualization_be.dto.overview.command.UtilityOverviewKpiDto;
import com.example.factory_utility_visualization_be.repository.overview.command.UtilityOverviewKpiRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UtilityOverviewKpiService {

	private final UtilityOverviewKpiRepo repo;

	public UtilityOverviewKpiDto getKpi(
			String cate,
			String factory,
			String period
	) {
		String safeCate = normalizeCate(cate);
		String safeFactory = normalizeFactory(factory);


		DateRange current = resolveRange(period);
		DateRange previous = previousRange(current);

		UsageTotal today = readTotal(
				repo.findTotalUsage(
						safeCate,
						"Total Energy Consumption",
						safeFactory,
						current.from(),
						current.to()
				)
		);

		UsageTotal prev = readTotal(
				repo.findTotalUsage(
						safeCate,
						"Total Energy Consumption",
						safeFactory,
						previous.from(),
						previous.to()
				)
		);

		PeakFactory peak = readPeak(
				repo.findPeakFactory(
						safeCate,
						"Total Energy Consumption",
						safeFactory,
						current.from(),
						current.to()
				)
		);

		BigDecimal energyDiffPercent = percentDiff(today.value(), prev.value());

		BigDecimal exchange = BigDecimal.valueOf(25000);
		BigDecimal sepzone = BigDecimal.ONE;

		CostTotal todayCostTotal = readCost(
				repo.findTotalCost(
						safeCate,
						"Total Energy Consumption",
						safeFactory,
						current.from(),
						current.to(),
						exchange,
						sepzone
				)
		);

		CostTotal prevCostTotal = readCost(
				repo.findTotalCost(
						safeCate,
						"Total Energy Consumption",
						safeFactory,
						previous.from(),
						previous.to(),
						exchange,
						sepzone
				)
		);

		BigDecimal todayCost = todayCostTotal.usd()
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal prevCost = prevCostTotal.usd()
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal costDiffPercent = percentDiff(todayCost, prevCost);

		BigDecimal peakShare = BigDecimal.ZERO;
		if (today.value().compareTo(BigDecimal.ZERO) > 0) {
			peakShare = peak.value()
					.multiply(BigDecimal.valueOf(100))
					.divide(today.value(), 1, RoundingMode.HALF_UP);
		}

		Integer totalSignals = nullToZero(repo.countSignals(safeCate, safeFactory));

		LocalDateTime staleTime = LocalDateTime.now().minusMinutes(5);
		Integer staleCount = nullToZero(
				repo.countStaleOrNoDataSignals(safeCate, safeFactory, staleTime)
		);

		Integer noDataCount = staleCount;

		BigDecimal dataHealthPercent = BigDecimal.ZERO;
		if (totalSignals > 0) {
			int ok = Math.max(totalSignals - staleCount, 0);

			dataHealthPercent = BigDecimal.valueOf(ok)
					.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(totalSignals), 0, RoundingMode.HALF_UP);
		}

		return new UtilityOverviewKpiDto(
				today.value(),
				today.unit(),
				energyDiffPercent,

				todayCost,
				"USD",
				costDiffPercent,

				0,
				0,

				peak.fac(),
				peak.value(),
				peakShare,

				dataHealthPercent,
				noDataCount,
				staleCount
		);
	}

	private String normalizeCate(String cate) {
		if (cate == null || cate.isBlank()) return "Electricity";

		return switch (cate.trim().toLowerCase()) {
			case "water" -> "Water";
			case "compressed air", "compressor_air", "air" -> "Compressed Air";
			default -> "Electricity";
		};
	}

	private String normalizeFactory(String factory) {
		if (factory == null || factory.isBlank()) return "All Factory";

		return switch (factory.trim().toLowerCase()) {
			case "fac a", "fac_a", "faca" -> "Fac_A";
			case "fac b", "fac_b", "facb" -> "Fac_B";
			case "fac c", "fac_c", "facc" -> "Fac_C";
			case "kvh", "all", "all factory" -> "All Factory";
			default -> factory.trim();
		};
	}


	private CostTotal readCost(Object raw) {
		Object[] arr = unwrapRow(raw);

		if (arr == null || arr.length == 0) {
			return new CostTotal(BigDecimal.ZERO, BigDecimal.ZERO);
		}

		BigDecimal vnd = decimal(arr[0]);
		BigDecimal usd = arr.length > 1 ? decimal(arr[1]) : BigDecimal.ZERO;

		return new CostTotal(vnd, usd);
	}

	private record CostTotal(BigDecimal vnd, BigDecimal usd) {}

	private DateRange resolveRange(String period) {
		LocalDate today = LocalDate.now();

		if (period == null || period.isBlank()) {
			return new DateRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
		}

		return switch (period.trim().toLowerCase()) {
			case "yesterday" -> new DateRange(
					today.minusDays(1).atStartOfDay(),
					today.atStartOfDay()
			);
			case "this week" -> {
				LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - 1);
				yield new DateRange(start.atStartOfDay(), today.plusDays(1).atStartOfDay());
			}
			case "this month" -> {
				LocalDate start = today.withDayOfMonth(1);
				yield new DateRange(start.atStartOfDay(), today.plusDays(1).atStartOfDay());
			}
			default -> new DateRange(
					today.atStartOfDay(),
					today.plusDays(1).atStartOfDay()
			);
		};
	}

	private DateRange previousRange(DateRange current) {
		long days = java.time.Duration.between(current.from(), current.to()).toDays();

		if (days <= 0) days = 1;

		return new DateRange(
				current.from().minusDays(days),
				current.from()
		);
	}

	private UsageTotal readTotal(Object raw) {
		Object[] arr = unwrapRow(raw);

		if (arr == null || arr.length == 0) {
			return new UsageTotal(BigDecimal.ZERO, "kWh");
		}

		BigDecimal value = decimal(arr[0]);
		String unit = arr.length > 1 && arr[1] != null
				? arr[1].toString()
				: "kWh";

		return new UsageTotal(value, unit);
	}

	private PeakFactory readPeak(Object raw) {
		Object[] arr = unwrapRow(raw);

		if (arr == null || arr.length == 0) {
			return new PeakFactory("-", BigDecimal.ZERO);
		}

		String fac = arr[0] != null ? arr[0].toString() : "-";
		BigDecimal value = arr.length > 1 ? decimal(arr[1]) : BigDecimal.ZERO;

		return new PeakFactory(fac, value);
	}

	private Object[] unwrapRow(Object raw) {
		if (raw == null) return null;

		if (raw instanceof Object[] arr) {
			if (arr.length == 1 && arr[0] instanceof Object[] nested) {
				return nested;
			}

			return arr;
		}

		return null;
	}

	private BigDecimal percentDiff(BigDecimal current, BigDecimal previous) {
		if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}

		return current.subtract(previous)
				.multiply(BigDecimal.valueOf(100))
				.divide(previous, 1, RoundingMode.HALF_UP);
	}

	private BigDecimal decimal(Object value) {
		if (value == null) return BigDecimal.ZERO;

		if (value instanceof BigDecimal bd) return bd;

		if (value instanceof Integer i) return BigDecimal.valueOf(i);
		if (value instanceof Long l) return BigDecimal.valueOf(l);
		if (value instanceof Double d) return BigDecimal.valueOf(d);
		if (value instanceof Float f) return BigDecimal.valueOf(f);

		String text = value.toString()
				.replace(",", "")
				.trim();

		if (text.isBlank()) return BigDecimal.ZERO;

		return new BigDecimal(text);
	}

	private Integer nullToZero(Integer value) {
		return value == null ? 0 : value;
	}

	private record DateRange(LocalDateTime from, LocalDateTime to) {
	}

	private record UsageTotal(BigDecimal value, String unit) {
	}

	private record PeakFactory(String fac, BigDecimal value) {
	}
}