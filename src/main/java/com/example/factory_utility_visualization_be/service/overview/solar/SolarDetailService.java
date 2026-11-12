package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarDashboardProjection;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.*;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarHourEnergyProjection;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import com.example.factory_utility_visualization_be.service.util.SolarEnvironmentCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SolarDetailService {

	private static final String ENERGY_NAME =
			"Total Energy Consumption";

	private static final String POWER_NAME =
			"Total Power";

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private final SolarDashboardRepo repo;
	private final SolarMonthlySummaryCacheService summaryCache;
	private final ElectricityPricingService pricingService;

	@Transactional(readOnly = true)
	public SolarDetailDto getDetail(
			String facId,
			String month
	) {

		final String fac =
				normalizeFac(facId);

		final YearMonth ym =
				YearMonth.parse(
						month,
						DateTimeFormatter.ofPattern("yyyyMM")
				);

		final LocalDateTime monthStart =
				ym.atDay(1)
						.atStartOfDay();

		final LocalDateTime nextMonthStart =
				ym.plusMonths(1)
						.atDay(1)
						.atStartOfDay();

		final LocalDateTime now =
				LocalDateTime.now(APP_ZONE);

		// =========================================================
		// SUMMARY
		//
		// Current month: always fresh, never cached (accumulating MTD
		// totals + live current power). Completed months: cached, since
		// the query window is closed and the result cannot change.
		// =========================================================

		final SolarDashboardProjection summaryRow =
				ym.equals(YearMonth.now(APP_ZONE))
						? repo.getSolarDashboardByMonth(
								fac,
								monthStart,
								nextMonthStart,
								now.plusSeconds(1),
								POWER_NAME,
								ENERGY_NAME,
								pricingService.excludedMeterKeys()
						)
						: summaryCache.getHistoricalSummary(
								fac,
								ym.toString(),
								monthStart,
								nextMonthStart,
								now.plusSeconds(1),
								POWER_NAME,
								ENERGY_NAME
						);

		final BigDecimal solarKwh =
				value(
						summaryRow == null
								? null
								: summaryRow.getSolarKwh()
				);

		final SolarDetailSummaryDto summary =
				new SolarDetailSummaryDto(

						value(
								summaryRow == null
										? null
										: summaryRow.getCurrentPowerKw()
						),

						solarKwh,

						value(
								summaryRow == null
										? null
										: summaryRow.getGridKwh()
						),

						value(
								summaryRow == null
										? null
										: summaryRow.getTotalKwh()
						),

						value(
								summaryRow == null
										? null
										: summaryRow.getSolarSharePercent()
						)
				);


		// =========================================================
		// DAILY TREND
		// =========================================================

		final List<SolarDailyTrendDto> dailyTrend =
				repo.getSolarDailyTrend(
								fac,
								monthStart,
								nextMonthStart,
								ENERGY_NAME,
								pricingService.excludedMeterKeys()
						)
						.stream()
						.map(row ->
								new SolarDailyTrendDto(
										row.getRecordDate(),
										value(row.getSolarKwh()),
										value(row.getGridKwh()),
										value(row.getTotalKwh()),
										value(row.getSolarSharePercent())
								)
						)
						.toList();


		// =========================================================
		// COST
		//
		// ElectricityPricingService (same rules as every electricity
		// API, "F2 electric consumption.xlsx"):
		// - normal = solar kWh priced as grid (tariff VND, grid $)
		// - solar  = solar kWh at the solar price (tariff VND x
		//            solar-price-factor per band, solar $)
		// - saving = normal - solar
		// History_Main rows are stamped at the END of their hour, so
		// the window is shifted by +1h.
		// =========================================================

		final SolarCostImpactDto costImpact =
				costImpact(
						repo.findSolarHourlyEnergy(
								fac,
								monthStart.plusHours(1),
								nextMonthStart.plusHours(1),
								ENERGY_NAME,
								pricingService.excludedMeterKeys()
						)
				);


		// =========================================================
		// ENVIRONMENT
		// =========================================================

		final BigDecimal co2Kg =
				SolarEnvironmentCalculator.co2Kg(solarKwh);

		final SolarEnvironmentalDto environment =
				new SolarEnvironmentalDto(

						co2Kg,

						SolarEnvironmentCalculator.co2Ton(co2Kg),

						SolarEnvironmentCalculator.equivalentTrees(co2Kg),

						SolarEnvironmentCalculator.CO2_FACTOR
				);


		// =========================================================
		// HOURLY PROFILE
		//
		// Nếu đang xem tháng hiện tại -> hôm nay.
		// Nếu tháng cũ -> lấy ngày cuối cùng có data.
		// =========================================================

		LocalDate profileDate;

		final YearMonth currentYm =
				YearMonth.from(now);

		if (ym.equals(currentYm)) {
			profileDate = now.toLocalDate();
		} else if (!dailyTrend.isEmpty()) {
			profileDate =
					dailyTrend
							.get(dailyTrend.size() - 1)
							.date();
		} else {
			profileDate =
					ym.atEndOfMonth();
		}

		final LocalDateTime dayStart =
				profileDate.atStartOfDay();

		final LocalDateTime nextDayStart =
				dayStart.plusDays(1);

		final List<SolarHourlyPointDto> hourlyPoints =
				repo.getSolarHourlyProfile(
								fac,
								dayStart,
								nextDayStart,
								ENERGY_NAME,
								pricingService.excludedMeterKeys()
						)
						.stream()
						.map(row ->
								new SolarHourlyPointDto(
										row.getScaleHour(),
										value(row.getEnergyKwh())
								)
						)
						.toList();

		final BigDecimal totalHourly =
				hourlyPoints
						.stream()
						.map(SolarHourlyPointDto::energyKwh)
						.reduce(
								BigDecimal.ZERO,
								BigDecimal::add
						);

		final SolarHourlyPointDto peak =
				hourlyPoints
						.stream()
						.max(
								Comparator.comparing(
										SolarHourlyPointDto::energyKwh
								)
						)
						.orElse(null);

		final SolarHourlyProfileDto hourlyProfile =
				new SolarHourlyProfileDto(

						profileDate,

						totalHourly,

						peak == null
								? BigDecimal.ZERO
								: peak.energyKwh(),

						peak == null
								? null
								: peak.hour(),

						hourlyPoints
				);


		return new SolarDetailDto(
				fac,
				month,
				now,
				summary,
				dailyTrend,
				costImpact,
				environment,
				hourlyProfile
		);
	}


	// money null when no tariff version is usable at all; hours without a
	// tariff version are left out of the money and flagged (priceMissing)
	private SolarCostImpactDto costImpact(
			List<SolarHourEnergyProjection> rows
	) {

		BigDecimal kwh = BigDecimal.ZERO;
		BigDecimal normalVnd = BigDecimal.ZERO;
		BigDecimal solarVnd = BigDecimal.ZERO;
		BigDecimal normalUsd = BigDecimal.ZERO;
		BigDecimal solarUsd = BigDecimal.ZERO;
		boolean priceMissing = false;

		final Optional<Pricing> pricing =
				pricingService.load();

		for (SolarHourEnergyProjection row : rows) {

			if (row.getHourAt() == null || row.getKwh() == null) {
				continue;
			}

			kwh = kwh.add(row.getKwh());

			final List<TouShare> shares = pricing
					.map(p -> p.shares(row.getHourAt()))
					.orElse(List.of());

			if (shares.isEmpty()) {
				priceMissing |= row.getKwh().signum() != 0;
				continue;
			}

			for (TouShare share : shares) {
				normalVnd = normalVnd.add(pricing.get().gridVnd(row.getKwh(), share));
				solarVnd = solarVnd.add(pricing.get().solarVnd(row.getKwh(), share));
				normalUsd = normalUsd.add(pricing.get().gridUsd(row.getKwh(), share));
				solarUsd = solarUsd.add(pricing.get().solarUsd(row.getKwh(), share));
			}
		}

		final BigDecimal savingUsd =
				normalUsd.subtract(solarUsd);

		if (pricing.isEmpty()) {
			return new SolarCostImpactDto(
					money(kwh), null, null, null, null, null, null, null,
					kwh.signum() != 0
			);
		}

		return new SolarCostImpactDto(
				money(kwh),
				money(normalVnd),
				money(solarVnd),
				money(normalVnd.subtract(solarVnd)),
				money(normalUsd),
				money(solarUsd),
				money(savingUsd),
				normalUsd.signum() == 0
						? BigDecimal.ZERO.setScale(1)
						: savingUsd.multiply(BigDecimal.valueOf(100))
								.divide(normalUsd, 1, RoundingMode.HALF_UP),
				priceMissing
		);
	}

	private BigDecimal money(
			BigDecimal value
	) {
		return value.setScale(
				2,
				RoundingMode.HALF_UP
		);
	}


	private BigDecimal value(
			BigDecimal value
	) {

		return value == null
				? BigDecimal.ZERO
				: value;
	}


	private String normalizeFac(
			String facId
	) {
		return FacilityValidator.normalizeOptionalWithDefault(facId);
	}
}
