package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyQueryRange;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.EnergyHour;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.MonthlyResult;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.RawRow;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Money;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityMonthlyQueryService {

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String KVH = "KVH";
	private static final String ELECTRICITY = "Electricity";

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private static final BigDecimal ZERO_2 = BigDecimal.ZERO.setScale(2);

	// EnergyHour.period of UtilityMonthlyJdbcRepository
	private static final String PERIOD_CURRENT = "CURRENT";
	private static final String PERIOD_PREV = "PREV";

	private final UtilityMonthlyJdbcRepository repository;
	private final ElectricityPricingService pricingService;

	@Transactional(readOnly = true)
	public List<MonthlySummaryDto> query(
			String fac,
			String monthYyyyMm,
			MonthlyQueryRange range
	) {
		final String normalizedFac = normalizeFac(fac);

		log.info(
				"Query monthly DB: fac={}, month={}, from={}, currentTo={}, prevFrom={}, prevTo={}",
				normalizedFac,
				monthYyyyMm,
				range.from(),
				range.currentTo(),
				range.prevFrom(),
				range.prevTo()
		);

		final MonthlyResult result = repository.sumMonthly(
				normalizedFac,
				KVH.equalsIgnoreCase(normalizedFac),
				range.from(),
				range.currentTo(),
				range.prevFrom(),
				range.prevTo(),
				pricingService.excludedMeterKeys()
		);

		if (result.rows().isEmpty()) {
			return List.of();
		}

		final Map<String, PeriodCost> costs = price(result.energyHours());

		final OffsetDateTime generatedAt =
				OffsetDateTime.now(APP_ZONE);

		return result.rows().stream()
				.map(row -> toDto(row, monthYyyyMm, generatedAt, costs))
				.toList();
	}

	// VND / USD of one (period, unit); missing = some hour with kWh had no price
	private static final class PeriodCost {
		private Money money = new Money(BigDecimal.ZERO, BigDecimal.ZERO);
		private boolean missing;
	}

	private static String costKey(String period, String unit) {
		return period + "|" + (unit == null ? "" : unit);
	}

	/*
	 * Định giá từng giờ bằng ElectricityPricingService (nơi duy nhất tính tiền
	 * điện): version giá theo ngày của giờ. Giờ không có giá -> không cộng
	 * tiền và đánh dấu missing (vndCost/usdCost của kỳ đó vẫn là tổng các giờ
	 * có giá, priceMissing = true). Không có tariff/policy -> tiền null.
	 */
	private Map<String, PeriodCost> price(List<EnergyHour> hours) {
		final Optional<Pricing> pricing = pricingService.load();
		final Map<String, PeriodCost> costs = new HashMap<>();

		for (EnergyHour hour : hours) {
			if (hour.period() == null || hour.hourAt() == null || hour.kwh() == null) {
				continue;
			}

			final PeriodCost cost = costs.computeIfAbsent(costKey(hour.period(), hour.unit()), key -> new PeriodCost());
			final Optional<Money> money = pricing.flatMap(p -> p.gridCost(hour.hourAt(), hour.kwh()));

			if (money.isPresent()) {
				cost.money = cost.money.add(money.get());
			} else if (hour.kwh().signum() != 0) {
				cost.missing = true;
			}
		}

		if (pricing.isEmpty()) {
			// no usable tariff version at all (every hour lacks a version): money unknown
			costs.values().forEach(cost -> cost.money = null);
		}

		return costs;
	}

	/*
	 * Các trường dẫn xuất, giữ đúng scale và cách xử lý chia 0 của SQL cũ:
	 *
	 * - vndCost/usdCost = tổng tiền các giờ của kỳ (ElectricityPricingService,
	 *                    chung cho mọi API điện), scale 2; null khi không có
	 *                    tariff; priceMissing = có giờ không có version giá.
	 * - deltaValue     = (value|avgValue|0) - (prevValue|prevAvgValue|0), scale 2.
	 * - deltaPercent   = delta / prev * 100, scale 2; prev = 0 -> null.
	 * - totalEnergy    = value + solarValue (null coi như 0), scale 2; chỉ dòng Electricity.
	 * - solarShare     = solarValue / totalEnergy * 100, scale 2; chỉ khi total > 0.
	 */
	private MonthlySummaryDto toDto(
			RawRow row,
			String month,
			OffsetDateTime generatedAt,
			Map<String, PeriodCost> costs
	) {
		final boolean electricity = ELECTRICITY.equals(row.cate());

		final PeriodCost cost = electricity ? costs.get(costKey(PERIOD_CURRENT, row.unit())) : null;
		final PeriodCost prevCost = electricity ? costs.get(costKey(PERIOD_PREV, row.unit())) : null;

		final BigDecimal current = firstNonNullOrZero(row.value(), row.avgValue());
		final BigDecimal prev = firstNonNullOrZero(row.prevValue(), row.prevAvgValue());
		final BigDecimal delta = current.subtract(prev);

		final BigDecimal totalEnergy = electricity
				? sum(row.value(), row.solarValue())
				: null;

		final BigDecimal prevTotalEnergy = electricity
				? sum(row.prevValue(), row.prevSolarValue())
				: null;

		final BigDecimal solarShare = electricity && totalEnergy.signum() > 0
				? percent(orZero(row.solarValue()), totalEnergy)
				: null;

		return new MonthlySummaryDto(
				row.cate(),
				row.name(),
				month,

				row.minValue(),
				row.maxValue(),
				row.prevMinValue(),
				row.prevMaxValue(),

				row.value(),
				row.avgValue(),

				vnd(electricity, cost),
				usd(electricity, cost),

				row.prevValue(),
				row.prevAvgValue(),

				vnd(electricity, prevCost),
				usd(electricity, prevCost),

				delta.setScale(2, RoundingMode.HALF_UP),
				percent(delta, prev),

				row.unit(),
				row.pickAt(),

				// ============================
				// Solar monthly information
				// ============================
				row.solarValue(),
				row.prevSolarValue(),
				totalEnergy,
				prevTotalEnergy,
				solarShare,

				generatedAt,

				electricity ? priceMissing(cost) : null,
				electricity ? priceMissing(prevCost) : null
		);
	}

	// no energy hour in the period -> 0 (as before); no tariff -> null
	private static BigDecimal vnd(boolean electricity, PeriodCost cost) {
		if (!electricity) {
			return null;
		}

		if (cost == null) {
			return ZERO_2;
		}

		return cost.money == null ? null : cost.money.vnd().setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal usd(boolean electricity, PeriodCost cost) {
		if (!electricity) {
			return null;
		}

		if (cost == null) {
			return ZERO_2;
		}

		return cost.money == null ? null : cost.money.usd().setScale(2, RoundingMode.HALF_UP);
	}

	private static boolean priceMissing(PeriodCost cost) {
		return cost != null && (cost.missing || cost.money == null);
	}

	private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
		if (denominator.signum() == 0) {
			return null;
		}

		return numerator.multiply(HUNDRED)
				.divide(denominator, 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal sum(BigDecimal a, BigDecimal b) {
		return orZero(a).add(orZero(b)).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal firstNonNullOrZero(BigDecimal a, BigDecimal b) {
		return a != null ? a : orZero(b);
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private String normalizeFac(String fac) {
		if (fac == null || fac.isBlank()) {
			return KVH;
		}

		return fac.trim();
	}
}
