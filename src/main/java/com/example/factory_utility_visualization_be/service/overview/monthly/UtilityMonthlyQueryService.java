package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.config.UtilityFinanceProperties;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyQueryRange;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.RawRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityMonthlyQueryService {

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private static final String KVH = "KVH";
	private static final String ELECTRICITY = "Electricity";

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final UtilityMonthlyJdbcRepository repository;
	private final UtilityFinanceProperties financeProperties;

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

		final List<RawRow> rows = repository.sumMonthly(
				normalizedFac,
				KVH.equalsIgnoreCase(normalizedFac),
				range.from(),
				range.currentTo(),
				range.prevFrom(),
				range.prevTo()
		);

		if (rows.isEmpty()) {
			return List.of();
		}

		final OffsetDateTime generatedAt =
				OffsetDateTime.now(APP_ZONE);

		final BigDecimal exchange = financeProperties.getMonthly().getExchangeRate();
		final BigDecimal sepzone = financeProperties.getMonthly().getSepzone();

		return rows.stream()
				.map(row -> toDto(row, monthYyyyMm, exchange, sepzone, generatedAt))
				.toList();
	}

	/*
	 * Các trường dẫn xuất, giữ đúng scale và cách xử lý chia 0 của SQL cũ:
	 *
	 * - usdCost        = vndCost / exchange * sepzone, scale 2; exchange = 0/null -> null.
	 * - deltaValue     = (value|avgValue|0) - (prevValue|prevAvgValue|0), scale 2.
	 * - deltaPercent   = delta / prev * 100, scale 2; prev = 0 -> null.
	 * - totalEnergy    = value + solarValue (null coi như 0), scale 2; chỉ dòng Electricity.
	 * - solarShare     = solarValue / totalEnergy * 100, scale 2; chỉ khi total > 0.
	 */
	private MonthlySummaryDto toDto(
			RawRow row,
			String month,
			BigDecimal exchange,
			BigDecimal sepzone,
			OffsetDateTime generatedAt
	) {
		final boolean electricity = ELECTRICITY.equals(row.cate());

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

				row.vndCost(),
				toUsd(row.vndCost(), exchange, sepzone),

				row.prevValue(),
				row.prevAvgValue(),

				row.prevVndCost(),
				toUsd(row.prevVndCost(), exchange, sepzone),

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

				generatedAt
		);
	}

	private static BigDecimal toUsd(
			BigDecimal vnd,
			BigDecimal exchange,
			BigDecimal sepzone
	) {
		if (vnd == null || exchange == null || exchange.signum() == 0 || sepzone == null) {
			return null;
		}

		return vnd.divide(exchange, MathContext.DECIMAL128)
				.multiply(sepzone)
				.setScale(2, RoundingMode.HALF_UP);
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
