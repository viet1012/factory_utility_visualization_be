package com.example.factory_utility_visualization_be.service.overview.monthly;

import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlyQueryRange;
import com.example.factory_utility_visualization_be.dto.overview.monthly.MonthlySummaryDto;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.EnvAgg;
import com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.RawRow;
import com.example.factory_utility_visualization_be.service.overview.electric.DashboardEnergyService;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.HourSum;
import com.example.factory_utility_visualization_be.service.overview.electric.GridSolarSplit.Totals;
import com.example.factory_utility_visualization_be.service.overview.electric.HourlyEnergyCache;
import com.example.factory_utility_visualization_be.service.overview.electric.HourlyEnergyCache.MeterHour;
import com.example.factory_utility_visualization_be.service.overview.electric.OfficialEnergyService;
import com.example.factory_utility_visualization_be.service.overview.electric.OfficialEnergyService.OfficialTotal;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.EvnDay;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.PERIOD_CURRENT;
import static com.example.factory_utility_visualization_be.repository.overview.monthly.UtilityMonthlyJdbcRepository.PERIOD_PREV;

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
	private final ElectricityPricingService pricingService;
	private final DashboardEnergyService dashboardEnergy;
	private final HourlyEnergyCache hourlyCache;
	private final MonthlyEnvCache envCache;
	private final OfficialEnergyService officialEnergy;

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

		final long started = System.nanoTime();
		final String facKey = GridSolarSplit.facKey(normalizedFac);

		/*
		 * Electricity: load / solar / grid theo giờ từ DashboardEnergyService
		 * (HourlyEnergyCache, dùng chung KVH / Fac_A/B/C), một cửa sổ
		 * [prevFrom, currentTo) như trước (outlier median trên đúng cửa sổ này):
		 * grid = max(load - solar, 0) theo giờ, tiền = grid theo giá grid.
		 */
		final Optional<Pricing> pricing = pricingService.load();
		final Map<LocalDateTime, HourSum> hours =
				dashboardEnergy.facHours(normalizedFac, range.prevFrom(), range.currentTo());
		// same cached months (already loaded above): unit / pickAt of the Electricity row
		final List<MeterHour> meterHours = hourlyCache.rows(range.prevFrom(), range.currentTo());
		final long hourlyNs = System.nanoTime();

		final Totals current = GridSolarSplit.totals(hours, range.from(), range.currentTo(), pricing);
		final Totals prev = GridSolarSplit.totals(hours, range.prevFrom(), range.prevTo(), pricing);
		final boolean priced = pricing.isPresent();
		final long pricedNs = System.nanoTime();

		// official: per day EVN (KVH / FAC_C) when imported, else the measured grid
		final Map<LocalDate, EvnDay> evn = officialEnergy.evnDays(normalizedFac,
				range.prevFrom().toLocalDate(), range.currentTo().toLocalDate().plusDays(1));
		final OfficialTotal official = OfficialEnergyService.total(
				officialEnergy.dailyOfficial(hours, range.from(), range.currentTo(), evn, pricing));
		final OfficialTotal officialPrev = OfficialEnergyService.total(
				officialEnergy.dailyOfficial(hours, range.prevFrom(), range.prevTo(), evn, pricing));
		final long officialNs = System.nanoTime();

		/*
		 * Water / Air: tháng đã đóng -> MonthlyEnvCache (1 SQL cho mọi fac,
		 * cache theo tháng); tháng đang chạy -> SQL của đúng fac (cửa sổ tới now).
		 */
		final boolean closedMonth = range.currentTo().equals(range.from().plusMonths(1));
		final List<EnvAgg> env = (closedMonth
				? envCache.closedMonth(YearMonth.from(range.from()), range)
				: repository.envSummary(facKey, range.from(), range.currentTo(), range.prevFrom(), range.prevTo()))
				.stream()
				.filter(agg -> facKey.equals(agg.facKey()))
				.toList();
		final long envNs = System.nanoTime();

		final List<RawRow> rows = buildRows(facKey, range, meterHours, env);

		if (rows.isEmpty()) {
			return List.of();
		}

		final OffsetDateTime generatedAt =
				OffsetDateTime.now(APP_ZONE);

		final List<MonthlySummaryDto> dtos = rows.stream()
				.map(row -> ELECTRICITY.equals(row.cate())
						? electricityDto(row, monthYyyyMm, generatedAt, current, prev, priced, official, officialPrev)
						: envDto(row, monthYyyyMm, generatedAt))
				.toList();

		log.info("monthly-summary {} {}: hourly energy {} ms, pricing {} ms, official ({}) {} ms, water/air {} {} ms, total {} ms",
				normalizedFac, monthYyyyMm,
				(hourlyNs - started) / 1_000_000,
				(pricedNs - hourlyNs) / 1_000_000,
				OfficialEnergyService.describe(normalizedFac, official),
				(officialNs - pricedNs) / 1_000_000,
				closedMonth ? "(month cache)" : "SQL",
				(envNs - officialNs) / 1_000_000,
				(System.nanoTime() - started) / 1_000_000);

		return dtos;
	}

	// kind of EnvAgg -> name / cate of the summary row, in the old ORDER BY ord (2, 3, 4)
	private record EnvName(String kind, String name, String cate) {}

	private static final List<EnvName> ENV_NAMES = List.of(
			new EnvName("COOLING", "Cooling Tank Temperature", "Water"),
			new EnvName("PIPELINE", "Data Pipeline pressure", "Water"),
			new EnvName("AIR", "Sensor compressed air pressure Data", "Compressed Air")
	);

	private static final Comparator<String> UNIT_ORDER =
			Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

	/*
	 * Các dòng summary, giống SQL cũ (FinalRows ORDER BY ord):
	 * - Electricity (ord 1): một dòng / unit của các meter lưới (không SOLAR)
	 *   của fac có dữ liệu trong kỳ hiện tại hoặc kỳ trước; không có -> không có dòng.
	 * - Water / Air (ord 2..4): Fac luôn đủ 3 dòng, KVH chỉ dòng có dữ liệu;
	 *   min / max / avg làm tròn 1 chữ số, unit = MAX(unit) của cả 2 kỳ.
	 * - pickAt (mọi dòng) = pick_at mới nhất của kỳ hiện tại, điện lưới + môi trường.
	 */
	private List<RawRow> buildRows(
			String facKey,
			MonthlyQueryRange range,
			List<MeterHour> meterHours,
			List<EnvAgg> env
	) {
		final TreeSet<String> units = new TreeSet<>(UNIT_ORDER);
		LocalDateTime pickAt = null;

		for (MeterHour row : meterHours) {
			if (row.solar() || !GridSolarSplit.facIncludes(facKey, row.fac())) {
				continue;
			}

			final boolean inCurrent = within(row.hourAt(), range.from(), range.currentTo());

			if (inCurrent || within(row.hourAt(), range.prevFrom(), range.prevTo())) {
				units.add(row.unit());
			}

			if (inCurrent) {
				pickAt = later(pickAt, row.lastPickAt());
			}
		}

		for (EnvAgg agg : env) {
			if (PERIOD_CURRENT.equals(agg.period())) {
				pickAt = later(pickAt, agg.maxPickAt());
			}
		}

		final List<RawRow> rows = new ArrayList<>();

		for (String unit : units) {
			rows.add(new RawRow(
					ELECTRICITY, "Total Energy Consumption", unit,
					null, null, null, null,
					null, null, null, null,
					pickAt,
					null, null));
		}

		final boolean kvh = KVH.equals(facKey);

		for (EnvName envName : ENV_NAMES) {
			EnvAgg cur = null;
			EnvAgg prv = null;

			for (EnvAgg agg : env) {
				if (envName.kind().equals(agg.kind())) {
					if (PERIOD_CURRENT.equals(agg.period())) {
						cur = agg;
					} else if (PERIOD_PREV.equals(agg.period())) {
						prv = agg;
					}
				}
			}

			if (kvh && cur == null && prv == null) {
				continue;
			}

			rows.add(new RawRow(
					envName.cate(), envName.name(), maxUnit(cur, prv),
					round1(cur == null ? null : cur.min()),
					round1(cur == null ? null : cur.max()),
					round1(prv == null ? null : prv.min()),
					round1(prv == null ? null : prv.max()),
					null, avg1(cur),
					null, avg1(prv),
					pickAt,
					null, null));
		}

		return rows;
	}

	private static boolean within(LocalDateTime at, LocalDateTime from, LocalDateTime to) {
		return !at.isBefore(from) && at.isBefore(to);
	}

	private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
		if (a == null) {
			return b;
		}

		return b != null && b.isAfter(a) ? b : a;
	}

	// MAX(unit) of both periods (case-insensitive like the SQL collation)
	private static String maxUnit(EnvAgg cur, EnvAgg prv) {
		String a = cur == null ? null : cur.unit();
		String b = prv == null ? null : prv.unit();

		if (a == null) {
			return b;
		}

		return b != null && String.CASE_INSENSITIVE_ORDER.compare(b, a) > 0 ? b : a;
	}

	// CAST(ROUND(x, 1) AS DECIMAL(18,1)); values are > 0, so HALF_UP = SQL ROUND
	private static BigDecimal round1(BigDecimal value) {
		return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
	}

	// AVG(value) (decimal, scale 6) rounded to 1 digit
	private static BigDecimal avg1(EnvAgg agg) {
		if (agg == null || agg.count() == 0 || agg.sum() == null) {
			return null;
		}

		return round1(agg.sum().divide(BigDecimal.valueOf(agg.count()), 6, RoundingMode.HALF_UP));
	}

	/*
	 * Dòng Electricity (scale 2):
	 * - value / prevValue           = grid = Σ giờ max(load - solar, 0) (điện mua lưới)
	 * - vndCost / usdCost (+ prev)  = tiền grid theo giá grid; null khi không có tariff;
	 *                                 priceMissing = có giờ có điện nhưng không có version giá
	 * - solarValue / prevSolarValue = solar
	 * - totalEnergyValue (+ prev)   = load = Σ sub-meter (tổng tiêu thụ)
	 * - solarSharePercent           = solar / load * 100; null khi load = 0
	 * - deltaValue / deltaPercent   = theo grid
	 */
	private MonthlySummaryDto electricityDto(
			RawRow row,
			String month,
			OffsetDateTime generatedAt,
			Totals current,
			Totals prev,
			boolean priced,
			OfficialTotal official,
			OfficialTotal officialPrev
	) {
		final BigDecimal value = scale2(current.grid());
		final BigDecimal prevValue = scale2(prev.grid());
		final BigDecimal delta = value.subtract(prevValue);
		final BigDecimal officialValue = scale2(official.gridKwh());
		final BigDecimal officialPrevValue = scale2(officialPrev.gridKwh());

		return new MonthlySummaryDto(
				row.cate(),
				row.name(),
				month,

				row.minValue(),
				row.maxValue(),
				row.prevMinValue(),
				row.prevMaxValue(),

				value,
				row.avgValue(),

				priced ? scale2(current.gridVnd()) : null,
				priced ? scale2(current.gridUsd()) : null,

				prevValue,
				row.prevAvgValue(),

				priced ? scale2(prev.gridVnd()) : null,
				priced ? scale2(prev.gridUsd()) : null,

				delta.setScale(2, RoundingMode.HALF_UP),
				percent(delta, prevValue),

				row.unit(),
				row.pickAt(),

				// ============================
				// Solar monthly information
				// ============================
				scale2(current.solar()),
				scale2(prev.solar()),
				scale2(current.load()),
				scale2(prev.load()),
				current.load().signum() > 0 ? percent(current.solar(), current.load()) : null,

				generatedAt,

				!priced || current.priceMissing(),
				!priced || prev.priceMissing(),

				// official (EVN per day when imported, else measured)
				officialValue,
				priced ? scale2(official.usd()) : null,
				priced ? scale2(official.vnd()) : null,
				officialPrevValue,
				priced ? scale2(officialPrev.usd()) : null,
				priced ? scale2(officialPrev.vnd()) : null,
				percent(officialValue.subtract(officialPrevValue), officialPrevValue),
				official.evnDays(),
				official.measuredDays(),
				officialPrev.evnDays(),
				officialPrev.measuredDays()
		);
	}

	/*
	 * Water / Air (giữ nguyên như SQL cũ):
	 * - deltaValue   = (value|avgValue|0) - (prevValue|prevAvgValue|0), scale 2.
	 * - deltaPercent = delta / prev * 100, scale 2; prev = 0 -> null.
	 */
	private MonthlySummaryDto envDto(
			RawRow row,
			String month,
			OffsetDateTime generatedAt
	) {
		final BigDecimal current = firstNonNullOrZero(row.value(), row.avgValue());
		final BigDecimal prev = firstNonNullOrZero(row.prevValue(), row.prevAvgValue());
		final BigDecimal delta = current.subtract(prev);

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

				null,
				null,

				row.prevValue(),
				row.prevAvgValue(),

				null,
				null,

				delta.setScale(2, RoundingMode.HALF_UP),
				percent(delta, prev),

				row.unit(),
				row.pickAt(),

				row.solarValue(),
				row.prevSolarValue(),
				null,
				null,
				null,

				generatedAt,

				null,
				null,

				// official: Electricity only
				null, null, null, null, null, null, null,
				null, null, null, null
		);
	}

	private static BigDecimal scale2(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
		if (denominator.signum() == 0) {
			return null;
		}

		return numerator.multiply(HUNDRED)
				.divide(denominator, 2, RoundingMode.HALF_UP);
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
