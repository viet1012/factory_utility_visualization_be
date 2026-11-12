package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.config.UtilityFinanceProperties;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageHourProjection;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
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
	private ElectricConsumptionCalculator calculator;

	@BeforeEach
	void setUp() {
		repo = mock(PowerUsageRepo.class);

		ElectricConsumptionProperties props = new ElectricConsumptionProperties();
		props.setGrid(policy("26005", "1.075"));
		props.setSolar(policy("26316", "1.0"));

		calculator = new ElectricConsumptionCalculator(repo, new TouTariffService(repo), props);

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
		when(repo.findHourlyEnergy(anyString(), anyList(), any(), any())).thenReturn(List.of(
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

		Scope kvh = scope(res, "KVH");
		Scope ab = scope(res, "FAC_AB");
		Scope c = scope(res, "FAC_C");

		assertThat(kvh.daily()).hasSize(31);
		assertThat(kvh.elapsedDays()).isEqualTo(31);

		DayPoint d21 = ab.daily().get(0);
		assertThat(d21.weekday()).isEqualTo("Fri");
		assertThat(d21.mwh().highPeak()).isEqualByComparingTo("0.100");
		assertThat(d21.mwh().normal()).isEqualByComparingTo("0");
		// 100 kWh * 3398 * 1.075 / 26005 = 14.047 $
		assertThat(d21.kusd().highPeak()).isEqualByComparingTo("0.014");

		DayPoint d22 = c.daily().get(1);
		assertThat(d22.mwh().solar()).isEqualByComparingTo("0.050");
		assertThat(d22.mwh().totalElectricity()).isEqualByComparingTo("0");
		assertThat(d22.mwh().total()).isEqualByComparingTo("0.050");

		DayPoint d24 = c.daily().get(3);
		assertThat(d24.mwh().normal()).isEqualByComparingTo("0.100");
		assertThat(d24.mwh().highPeak()).isEqualByComparingTo("0.100");

		assertThat(kvh.dailyTotalMwh().totalElectricity()).isEqualByComparingTo("0.300");
		assertThat(kvh.dailyTotalMwh().total()).isEqualByComparingTo("0.350");
		assertThat(ab.dailyTotalMwh().total()).isEqualByComparingTo("0.100");

		// monthly = per-day average: 3.1 MWh / 31 days, low peak (02:00)
		assertThat(kvh.monthly()).hasSize(1);
		assertThat(kvh.monthly().get(0).month()).isEqualTo("2026-08");
		assertThat(kvh.monthly().get(0).from()).isEqualTo(LocalDate.of(2026, 7, 21).toString());
		assertThat(kvh.monthly().get(0).perDayMwh().lowPeak()).isEqualByComparingTo("0.100");
		assertThat(ab.monthly().get(0).perDayMwh().total()).isEqualByComparingTo("0.100");
		assertThat(c.monthly().get(0).perDayMwh().total()).isEqualByComparingTo("0");
	}

	@Test
	void billMonthRollsOverOnCutoffDay() {
		assertThat(calculator.billMonthOf(LocalDate.of(2026, 9, 20))).isEqualTo(YearMonth.of(2026, 9));
		assertThat(calculator.billMonthOf(LocalDate.of(2026, 9, 21))).isEqualTo(YearMonth.of(2026, 10));
	}

	private Scope scope(ElectricConsumptionResponseDto res, String code) {
		return res.scopes().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow();
	}

	private static UtilityFinanceProperties.Policy policy(String rate, String sepzone) {
		UtilityFinanceProperties.Policy policy = new UtilityFinanceProperties.Policy();
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
		};
	}

	private static PowerUsageHourProjection hour(String fac, int solar, String hourAt, String kwh) {
		return new PowerUsageHourProjection() {
			public String getFac() { return fac; }
			public Integer getSolar() { return solar; }
			public String getPurposeKey() { return null; }
			public LocalDateTime getHourAt() { return LocalDateTime.parse(hourAt); }
			public BigDecimal getKwh() { return new BigDecimal(kwh); }
		};
	}
}
