package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.config.ElectricityProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionHourProjection;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionMeterProjection;
import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElectricConsumptionCalculatorTest {

	private PowerUsageRepo repo;
	private ElectricConsumptionRepo energyRepo;
	private ElectricConsumptionProperties props;
	private ElectricityProperties electricity;
	private ElectricConsumptionCalculator calculator;

	@BeforeEach
	void setUp() {
		repo = mock(PowerUsageRepo.class);
		energyRepo = mock(ElectricConsumptionRepo.class);

		props = new ElectricConsumptionProperties();
		// single-hour fixtures count as complete days unless a test says otherwise
		props.setMinCoveragePct(BigDecimal.ONE);

		electricity = new ElectricityProperties();
		electricity.setGrid(policy("26005", "1.075"));
		electricity.setSolar(policy("26316", "1.0"));
		// no Java defaults any more: the factors come from application.yml only
		electricity.getSolarPriceFactor().setNormal(new BigDecimal("0.9"));
		electricity.getSolarPriceFactor().setHighPeak(new BigDecimal("0.9"));
		electricity.getSolarPriceFactor().setLowPeak(BigDecimal.ZERO);
		electricity.setHourOffset(0);

		calculator = new ElectricConsumptionCalculator(
				energyRepo,
				new ElectricityPricingService(new TouTariffService(repo), electricity),
				props);

		when(repo.findTariffs()).thenReturn(List.of(
				tariff("2-7", "4", "9.5", "1833"),
				tariff("2-7", "9.5", "11.5", "3398"),
				tariff("2-7", "11.5", "17", "1833"),
				tariff("2-7", "17", "20", "3398"),
				tariff("2-7", "20", "22", "1833"),
				tariff("2-7", "22", "4", "1190"),
				tariff("1", "4", "22", "1833"),
				tariff("1", "22", "4", "1190")
		));
	}

	@Test
	void splitsBandsPerScopeAndBillPeriod() {
		configuredMeters("Fac_A", "Fac_C");

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(List.of(
				// Fri 10:00 high peak, Fac_A grid
				hour("Fac_A", 0, "2026-08-21T10:00", "100"),
				// Sat 12:00 normal, Fac_C solar
				hour("Fac_C", 1, "2026-08-22T12:00", "50"),
				// Mon 09:00: half normal, half high peak
				hour("Fac_C", 0, "2026-08-24T09:00", "200"),
				// previous bill period (Jul 21 ~ Aug 20, 31 days)
				hour("Fac_B", 0, "2026-07-25T02:00", "3100")
		));

		ElectricConsumptionResponseDto res = calculator.calculate(YearMonth.of(2026, 9), 1);

		assertThat(res.billPeriod().label()).isEqualTo("Sep (Aug21 ~ Sep20]");
		assertThat(res.billPeriod().days()).isEqualTo(31);
		assertThat(res.unitPrices().gridNormal()).isEqualByComparingTo("0.0758");
		assertThat(res.unitPrices().solarNormal()).isEqualByComparingTo("0.0627");
		assertThat(res.unitPrices().solarHighPeak()).isEqualByComparingTo("0.1162");
		assertThat(res.unitPrices().solarLowPeak()).isEqualByComparingTo("0");

		Scope kvh = scope(res, "KVH");
		Scope ab = scope(res, "FAC_AB");
		Scope c = scope(res, "FAC_C");

		assertThat(kvh.daily()).hasSize(31);
		assertThat(kvh.elapsedDays()).isEqualTo(31);
		assertThat(kvh.daysWithData()).isEqualTo(3);

		// day without data -> null, not 0
		DayPoint d23 = kvh.daily().get(2);
		assertThat(d23.mwh()).isNull();
		assertThat(d23.kusd()).isNull();
		assertThat(d23.hoursWithData()).isZero();

		DayPoint d21 = ab.daily().get(0);
		assertThat(d21.weekday()).isEqualTo("Fri");
		assertThat(d21.mwh().highPeak()).isEqualByComparingTo("0.100");
		assertThat(d21.mwh().normal()).isEqualByComparingTo("0");
		// 100 kWh * 3398 * 1.075 / 26005 = 14.047 $
		assertThat(d21.kusd().highPeak()).isEqualByComparingTo("0.014");
		// Fac A&B has no SOLAR meter data -> solar null
		assertThat(d21.mwh().solar()).isNull();
		assertThat(d21.kusd().solar()).isNull();

		DayPoint d22 = c.daily().get(1);
		assertThat(d22.mwh().solar()).isEqualByComparingTo("0.050");
		assertThat(d22.mwh().totalElectricity()).isEqualByComparingTo("0");
		assertThat(d22.mwh().total()).isEqualByComparingTo("0.050");
		// solar only, no grid hour -> not complete
		assertThat(d22.partial()).isTrue();

		DayPoint d24 = c.daily().get(3);
		assertThat(d24.mwh().normal()).isEqualByComparingTo("0.100");
		assertThat(d24.mwh().highPeak()).isEqualByComparingTo("0.100");

		assertThat(kvh.dailyTotalMwh().totalElectricity()).isEqualByComparingTo("0.300");
		assertThat(kvh.dailyTotalMwh().total()).isEqualByComparingTo("0.350");
		assertThat(ab.dailyTotalMwh().total()).isEqualByComparingTo("0.100");

		// average over complete days only: d21 (0.1) + d24 (0.2 grid) / 2 days
		assertThat(kvh.completeDays()).isEqualTo(2);
		assertThat(kvh.dailyAverageMwh().totalElectricity()).isEqualByComparingTo("0.150");

		// monthly = average per complete day: 1 complete day with 3.1 MWh, low peak (02:00)
		assertThat(kvh.monthly()).hasSize(1);
		assertThat(kvh.monthly().get(0).month()).isEqualTo("2026-08");
		assertThat(kvh.monthly().get(0).label()).isEqualTo("Aug (Jul21 ~ Aug20]");
		assertThat(kvh.monthly().get(0).from()).isEqualTo(LocalDate.of(2026, 7, 21).toString());
		assertThat(kvh.monthly().get(0).completeDays()).isEqualTo(1);
		assertThat(kvh.monthly().get(0).partial()).isTrue();
		assertThat(kvh.monthly().get(0).perDayMwh().lowPeak()).isEqualByComparingTo("3.100");
		assertThat(kvh.monthly().get(0).perDayMwh().solar()).isNull();
		assertThat(ab.monthly().get(0).perDayMwh().total()).isEqualByComparingTo("3.100");
		// month without data -> null, not 0
		assertThat(c.monthly().get(0).perDayMwh()).isNull();
		assertThat(c.monthly().get(0).perDayKusd()).isNull();
	}

	@Test
	void daysWithMissingHoursArePartialAndLeftOutOfAverages() {
		props.setMinCoveragePct(BigDecimal.valueOf(100));
		configuredMeters("Fac_C");

		List<ElectricConsumptionHourProjection> rows = new java.util.ArrayList<>();

		// Aug 21: full 24 h of 10 kWh; Aug 22: only 23 h of 100 kWh
		for (int h = 0; h < 24; h++) {
			rows.add(hour("Fac_C", 0, String.format("2026-08-21T%02d:00", h), "10"));

			if (h != 5) {
				rows.add(hour("Fac_C", 0, String.format("2026-08-22T%02d:00", h), "100"));
			}
		}

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(rows);

		Scope c = scope(calculator.calculate(YearMonth.of(2026, 9), 0), "FAC_C");

		assertThat(c.daily().get(0).partial()).isFalse();
		assertThat(c.daily().get(0).hoursWithData()).isEqualTo(24);
		assertThat(c.daily().get(1).partial()).isTrue();
		assertThat(c.daily().get(1).hoursWithData()).isEqualTo(23);
		assertThat(c.daily().get(1).mwh().totalElectricity()).isEqualByComparingTo("2.300");

		assertThat(c.daysWithData()).isEqualTo(2);
		assertThat(c.completeDays()).isEqualTo(1);
		// total keeps every day, average only the complete one
		assertThat(c.dailyTotalMwh().totalElectricity()).isEqualByComparingTo("2.540");
		assertThat(c.dailyAverageMwh().totalElectricity()).isEqualByComparingTo("0.240");
	}

	@Test
	void dayWithMissingMeterIsPartialEvenWith24Hours() {
		props.setMinCoveragePct(BigDecimal.valueOf(100));
		configuredMeters("Fac_A", "Fac_A");

		List<ElectricConsumptionHourProjection> rows = new java.util.ArrayList<>();

		// Aug 21: 2 meters x 24 h; Aug 22: only 1 meter x 24 h
		for (int h = 0; h < 24; h++) {
			rows.add(hour("Fac_A", 0, String.format("2026-08-21T%02d:00", h), "20", 2));
			rows.add(hour("Fac_A", 0, String.format("2026-08-22T%02d:00", h), "5", 1));
		}

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(rows);

		Scope ab = scope(calculator.calculate(YearMonth.of(2026, 9), 0), "FAC_AB");

		assertThat(ab.meters()).isEqualTo(2);
		assertThat(ab.daily().get(0).partial()).isFalse();
		assertThat(ab.daily().get(0).coveragePct()).isEqualByComparingTo("100.0");
		assertThat(ab.daily().get(1).hoursWithData()).isEqualTo(24);
		assertThat(ab.daily().get(1).partial()).isTrue();
		assertThat(ab.daily().get(1).coveragePct()).isEqualByComparingTo("50.0");
		assertThat(ab.completeDays()).isEqualTo(1);
		assertThat(ab.dailyAverageMwh().totalElectricity()).isEqualByComparingTo("0.480");
	}

	@Test
	void pricesComeFromCostMaster() {
		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(List.of(
				// Sat 12:00 normal: 1000 kWh * 1833 * 1.075 / 26005 = 75.77 $
				hour("Fac_C", 0, "2026-08-22T12:00", "1000"),
				// Fri 10:00 high peak: 1000 kWh * 3398 * 1.075 / 26005 = 140.47 $
				hour("Fac_C", 0, "2026-08-21T10:00", "1000")
		));

		ElectricConsumptionResponseDto res = calculator.calculate(YearMonth.of(2026, 9), 0);
		Scope c = scope(res, "FAC_C");

		assertThat(res.unitPrices().gridNormal()).isEqualByComparingTo("0.0758");
		assertThat(res.unitPrices().gridHighPeak()).isEqualByComparingTo("0.1405");
		assertThat(c.daily().get(1).kusd().normal()).isEqualByComparingTo("0.076");
		assertThat(c.daily().get(0).kusd().highPeak()).isEqualByComparingTo("0.140");
	}

	@Test
	void outlierMeterHoursAreDroppedAndDayIsPartial() {
		props.setMinCoveragePct(BigDecimal.valueOf(100));
		configuredMeters("Fac_B");

		List<ElectricConsumptionHourProjection> rows = new java.util.ArrayList<>();

		for (int h = 0; h < 24; h++) {
			rows.add(hour("Fac_B", 0, String.format("2026-08-21T%02d:00", h), "50"));

			// Aug 22 16:00: cumulative value like P-6.1 3,401,705 kWh -> flagged by the repo
			rows.add(h == 16
					? hour("Fac_B", 0, "2026-08-22T16:00", "3401705", 1, 1)
					: hour("Fac_B", 0, String.format("2026-08-22T%02d:00", h), "50"));
		}

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(rows);

		Scope ab = scope(calculator.calculate(YearMonth.of(2026, 9), 0), "FAC_AB");
		DayPoint d22 = ab.daily().get(1);

		assertThat(ab.daily().get(0).partial()).isFalse();
		assertThat(ab.daily().get(0).excludedMwh()).isNull();
		assertThat(d22.partial()).isTrue();
		assertThat(d22.hoursWithData()).isEqualTo(23);
		assertThat(d22.excludedMwh()).isEqualByComparingTo("3401.705");
		// 23 h x 50 kWh, the outlier is not counted
		assertThat(d22.mwh().totalElectricity()).isEqualByComparingTo("1.150");
		assertThat(ab.completeDays()).isEqualTo(1);
		assertThat(ab.dailyAverageMwh().totalElectricity()).isEqualByComparingTo("1.200");
	}

	@Test
	void meterAddedMidPeriodDoesNotMakeEarlierHistoryDaysPartial() {
		props.setMinCoveragePct(BigDecimal.valueOf(98));

		List<ElectricConsumptionHourProjection> rows = new java.util.ArrayList<>();

		// previous bill period: Jul 21..Jul 30 1 meter; from Jul 31: 2 meters
		for (LocalDate day = LocalDate.of(2026, 7, 21); day.isBefore(LocalDate.of(2026, 8, 5)); day = day.plusDays(1)) {
			int devices = day.isBefore(LocalDate.of(2026, 7, 31)) ? 1 : 2;

			for (int h = 0; h < 24; h++) {
				rows.add(hour("Fac_C", 0, day.atTime(h, 0).toString(), "10", devices));
			}
		}

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(rows);

		// monthly history still takes expected meters from the data (+/- meterWindowDays)
		var month = scope(calculator.calculate(YearMonth.of(2026, 9), 1), "FAC_C").monthly().get(0);

		assertThat(month.meters()).isEqualTo(2);
		assertThat(month.daysWithData()).isEqualTo(15);
		// Jul 28..30 are within 3 days before the change: 2 expected meters -> 50 %
		assertThat(month.completeDays()).isEqualTo(12);
	}

	@Test
	void configuredMeterWithoutDataMakesCurrentDaysPartial() {
		props.setMinCoveragePct(BigDecimal.valueOf(98));
		// 2 configured meters, only 1 reports
		configuredMeters("Fac_C", "Fac_C");

		List<ElectricConsumptionHourProjection> rows = new java.util.ArrayList<>();

		for (int h = 0; h < 24; h++) {
			rows.add(hour("Fac_C", 0, String.format("2026-08-21T%02d:00", h), "10"));
		}

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(rows);

		Scope c = scope(calculator.calculate(YearMonth.of(2026, 9), 0), "FAC_C");

		assertThat(c.meters()).isEqualTo(2);
		assertThat(c.daily().get(0).meters()).isEqualTo(2);
		assertThat(c.daily().get(0).coveragePct()).isEqualByComparingTo("50.0");
		assertThat(c.daily().get(0).partial()).isTrue();
		assertThat(c.completeDays()).isZero();
	}

	@Test
	void hourOffsetMovesHoursIntoTheNextBand() {
		electricity.setHourOffset(1);

		// Fri 21:00 is normal; +1 h -> 22:00 low peak
		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList())).thenReturn(List.of(
				hour("Fac_C", 0, "2026-08-21T21:00", "100")
		));

		DayPoint d21 = scope(calculator.calculate(YearMonth.of(2026, 9), 0), "FAC_C").daily().get(0);

		assertThat(d21.mwh().normal()).isEqualByComparingTo("0");
		assertThat(d21.mwh().lowPeak()).isEqualByComparingTo("0.100");
	}

	@Test
	void meterMovedToFacAbCountsInFacAbNotFacC() {
		// SQL returns fac 'FAC_AB' for meters listed in meter-scope.fac-ab
		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList()))
				.thenReturn(List.of(hour("FAC_AB", 0, "2026-08-21T12:00", "100")));

		ElectricConsumptionResponseDto res = calculator.calculate(YearMonth.of(2026, 9), 0);

		assertThat(scope(res, "FAC_AB").daily().get(0).mwh().totalElectricity()).isEqualByComparingTo("0.100");
		assertThat(scope(res, "FAC_C").daily().get(0).mwh()).isNull();
		assertThat(scope(res, "KVH").daily().get(0).mwh().totalElectricity()).isEqualByComparingTo("0.100");
	}

	@Test
	void meterKeysAreUpperCaseAndNeverEmpty() {
		assertThat(ElectricityPricingService.meterKeys(null)).containsExactly("");
		assertThat(ElectricityPricingService.meterKeys(List.of(" ", ""))).containsExactly("");
		assertThat(ElectricityPricingService.meterKeys(List.of(" p-3.1_mfm384 ", "P-3.1_MFM384")))
				.containsExactly("P-3.1_MFM384");
	}

	@Test
	void dataBeforeHistoryStartIsNull() {
		props.setHistoryStartDate(LocalDate.of(2026, 8, 21));

		when(energyRepo.findHourlyEnergy(anyString(), any(), any(), any(), any(), any(), anyList(), anyList(), anyList()))
				.thenReturn(List.of(
						// before history start: ignored even if the repo returns it
						hour("Fac_C", 0, "2026-07-25T10:00", "500"),
						hour("Fac_C", 0, "2026-08-21T10:00", "100")
				));

		ElectricConsumptionResponseDto res = calculator.calculate(YearMonth.of(2026, 9), 1);
		Scope c = scope(res, "FAC_C");

		assertThat(res.historyStartDate()).isEqualTo("2026-08-21");
		assertThat(c.monthly().get(0).perDayMwh()).isNull();
		assertThat(c.monthly().get(0).meters()).isZero();
		assertThat(c.daily().get(0).mwh().totalElectricity()).isEqualByComparingTo("0.100");
	}

	@Test
	void billMonthRollsOverOnCutoffDay() {
		assertThat(calculator.billMonthOf(LocalDate.of(2026, 9, 20))).isEqualTo(YearMonth.of(2026, 9));
		assertThat(calculator.billMonthOf(LocalDate.of(2026, 9, 21))).isEqualTo(YearMonth.of(2026, 10));
	}

	// configured grid meters (findGridMeters), one per fac given
	private void configuredMeters(String... facs) {
		List<ElectricConsumptionMeterProjection> meters = new java.util.ArrayList<>();

		for (int i = 0; i < facs.length; i++) {
			String meter = "M-" + i;
			String fac = facs[i];
			meters.add(new ElectricConsumptionMeterProjection() {
				public String getMeter() { return meter; }
				public String getFac() { return fac; }
			});
		}

		when(energyRepo.findGridMeters(anyString(), anyList(), anyList(), anyList())).thenReturn(meters);
	}

	private Scope scope(ElectricConsumptionResponseDto res, String code) {
		return res.scopes().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
	}

	private static ElectricityProperties.Policy policy(String rate, String sepzone) {
		ElectricityProperties.Policy policy = new ElectricityProperties.Policy();
		policy.setExchangeRate(new BigDecimal(rate));
		policy.setSepzone(new BigDecimal(sepzone));
		return policy;
	}

	private static PowerUsageTariffProjection tariff(String wd, String fr, String to, String vnd) {
		return new PowerUsageTariffProjection() {
			public String getWd() { return wd; }
			public BigDecimal getFrTime() { return new BigDecimal(fr); }
			public BigDecimal getToTime() { return new BigDecimal(to); }
			public BigDecimal getVnd() { return new BigDecimal(vnd); }
			public java.time.LocalDateTime getEffectiveFrom() { return null; }
			public java.time.LocalDateTime getEffectiveTo() { return null; }
		};
	}

	private static ElectricConsumptionHourProjection hour(String fac, int solar, String hourAt, String kwh) {
		return hour(fac, solar, hourAt, kwh, 1, 0);
	}

	private static ElectricConsumptionHourProjection hour(String fac, int solar, String hourAt, String kwh, int devices) {
		return hour(fac, solar, hourAt, kwh, devices, 0);
	}

	private static ElectricConsumptionHourProjection hour(
			String fac, int solar, String hourAt, String kwh, int devices, int outlier) {
		return new ElectricConsumptionHourProjection() {
			public String getFac() { return fac; }
			public Integer getSolar() { return solar; }
			public LocalDateTime getHourAt() { return LocalDateTime.parse(hourAt); }
			public Integer getOutlier() { return outlier; }
			public BigDecimal getKwh() { return new BigDecimal(kwh); }
			public Integer getDevices() { return devices; }
		};
	}
}
