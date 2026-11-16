package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.config.ElectricConsumptionProperties;
import com.example.factory_utility_visualization_be.repository.overview.electric.ElectricConsumptionRepo;
import com.example.factory_utility_visualization_be.repository.overview.electric.projection.ElectricConsumptionHourProjection;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService;
import com.example.factory_utility_visualization_be.service.runtime.ElectricityPricingService.Pricing;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouBand;
import com.example.factory_utility_visualization_be.service.runtime.TouTariffService.TouShare;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * THE grid / solar split of every electricity view (electric-consumption,
 * monthly-summary, energy-daily, solar/monthly).
 *
 * The grid sub-meters measure the LOAD (consumption = grid + solar), not the
 * energy bought from EVN. Per scope and hour:
 *   load  = sum of the grid sub-meters (devices without SOLAR channel)
 *   solar = sum of the SOLAR devices of the scope
 *   grid  = max(load - solar, 0)          (bought from EVN)
 * Only grid is split into tariff bands and priced at the grid rate; solar is
 * priced at the solar rate (ElectricityPricingService).
 *
 * Hourly kWh: /electric-consumption -> hourSums (ElectricConsumptionRepo.
 * findHourlyEnergy, outliers flagged in SQL); dashboards -> DashboardEnergyService
 * (HourlyEnergyCache + isOutlier, same meters / filters / window / rule,
 * hour = pick_at - 1h + offset).
 * A solar meter belongs to the scope of its DeviceMap fac (SCADA fac or
 * utility.electric-consumption.meter-scope).
 */
@Service
@RequiredArgsConstructor
public class GridSolarSplit {

	public static final String ENERGY_NAME = "Total Energy Consumption";

	// fac key that includes every meter
	public static final String KVH = "KVH";

	private final ElectricConsumptionRepo repo;
	private final ElectricityPricingService pricingService;
	private final ElectricConsumptionProperties properties;

	// upper-case box_device_id lists for the repo (never empty)
	public record MeterFilter(List<String> excluded, List<String> facAb, List<String> facC) {}

	public MeterFilter meterFilter() {
		ElectricConsumptionProperties.MeterScope meterScope = properties.getMeterScope();

		return new MeterFilter(
				pricingService.excludedMeterKeys(),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacAb()),
				ElectricityPricingService.meterKeys(meterScope == null ? null : meterScope.getFacC()));
	}

	public static String facKey(String fac) {
		return fac == null ? "" : fac.trim().toUpperCase(Locale.ROOT);
	}

	/** History_Main rows of one scope x hour. */
	public static final class HourSum {
		private BigDecimal load = BigDecimal.ZERO;
		private BigDecimal solar = BigDecimal.ZERO;
		// kWh dropped as outliers (counts neither as load nor as solar)
		private BigDecimal excluded = BigDecimal.ZERO;
		private boolean hasLoad;
		private boolean hasSolar;
		// grid (load) meters reporting in that hour
		private int gridMeters;

		private void add(ElectricConsumptionHourProjection row) {
			add(row.getKwh(),
					row.getSolar() != null && row.getSolar() == 1,
					row.getOutlier() != null && row.getOutlier() == 1,
					row.getDevices() == null ? 1 : row.getDevices());
		}

		/**
		 * Adds kWh of `meters` meters of one hour (DashboardEnergyService:
		 * one cached meter-hour, meters = 1). Outliers count neither as load
		 * nor as solar nor as a reporting meter.
		 */
		public void add(BigDecimal kwh, boolean isSolar, boolean outlier, int meters) {
			if (outlier) {
				excluded = excluded.add(kwh);
				return;
			}

			if (isSolar) {
				solar = solar.add(kwh);
				hasSolar = true;
			} else {
				load = load.add(kwh);
				hasLoad = true;
				gridMeters += meters;
			}
		}

		// bought from EVN in this hour
		public BigDecimal grid() {
			return load.subtract(solar).max(BigDecimal.ZERO);
		}

		public BigDecimal load() {
			return load;
		}

		public BigDecimal solar() {
			return solar;
		}

		public BigDecimal excluded() {
			return excluded;
		}

		public boolean hasLoad() {
			return hasLoad;
		}

		public boolean hasSolar() {
			return hasSolar;
		}

		public int gridMeters() {
			return gridMeters;
		}
	}

	/**
	 * load / solar per scope x hour of [from, to) (hour start, offset applied).
	 *
	 * @param includes scope x fac key (upper case) -> the scope contains that fac
	 */
	public <K> Map<K, Map<LocalDateTime, HourSum>> hourSums(
			LocalDateTime from,
			LocalDateTime to,
			int offset,
			MeterFilter filter,
			Collection<K> scopes,
			BiPredicate<K, String> includes
	) {
		Map<K, Map<LocalDateTime, HourSum>> hours = new LinkedHashMap<>();

		for (K scope : scopes) {
			hours.put(scope, new TreeMap<>());
		}

		if (!from.isBefore(to)) {
			return hours;
		}

		// rows are stamped at the end of their hour; hourOffset shifts every
		// hour, so the query window is shifted the other way
		List<ElectricConsumptionHourProjection> rows = repo.findHourlyEnergy(
				ENERGY_NAME, from.plusHours(1L - offset), to.plusHours(1L - offset),
				properties.getOutlierRatio(), properties.getSolarOutlierRatio(), properties.getOutlierMinKwh(),
				filter.excluded(), filter.facAb(), filter.facC());

		for (ElectricConsumptionHourProjection row : rows) {
			if (row.getHourAt() == null || row.getKwh() == null) {
				continue;
			}

			LocalDateTime hourAt = row.getHourAt().plusHours(offset);

			if (hourAt.isBefore(from) || !hourAt.isBefore(to)) {
				continue;
			}

			String fac = facKey(row.getFac());

			for (K scope : scopes) {
				if (includes.test(scope, fac)) {
					hours.get(scope).computeIfAbsent(hourAt, key -> new HourSum()).add(row);
				}
			}
		}

		return hours;
	}

	// "KVH" = every meter, otherwise the meters whose fac key equals facKey(fac) (Fac_A -> FAC_A)
	public static boolean facIncludes(String facKey, String meterFacKey) {
		return KVH.equals(facKey) || facKey.equals(meterFacKey);
	}

	/**
	 * Java copy of the outlier rule of ElectricConsumptionRepo.findHourlyEnergy
	 * (CTE Marked) for one meter-hour, with the meter's median / P90 kWh per
	 * hour over the SAME window (PERCENTILE_CONT semantics, see percentile):
	 * - kWh <= minKwh                      -> never an outlier
	 * - SOLAR: kWh > solarRatio x P90      -> outlier
	 * - grid : kWh > ratio x median        -> outlier
	 * Keep both in sync.
	 */
	public static boolean isOutlier(
			BigDecimal kwh,
			boolean isSolar,
			BigDecimal median,
			BigDecimal p90,
			ElectricConsumptionProperties properties
	) {
		if (kwh.compareTo(properties.getOutlierMinKwh()) <= 0) {
			return false;
		}

		return isSolar
				? kwh.compareTo(p90.multiply(properties.getSolarOutlierRatio())) > 0
				: kwh.compareTo(median.multiply(properties.getOutlierRatio())) > 0;
	}

	/**
	 * SQL Server PERCENTILE_CONT(p) of ascending-sorted values: linear
	 * interpolation at row number p x (n - 1) (0-based).
	 */
	public static BigDecimal percentile(List<BigDecimal> sorted, double p) {
		int n = sorted.size();

		if (n == 0) {
			return BigDecimal.ZERO;
		}

		double rn = p * (n - 1);
		int lo = (int) Math.floor(rn);
		int hi = (int) Math.ceil(rn);
		BigDecimal low = sorted.get(lo);

		if (lo == hi) {
			return low;
		}

		BigDecimal fraction = BigDecimal.valueOf(rn - lo);
		return low.add(sorted.get(hi).subtract(low).multiply(fraction));
	}

	/** grid kWh / VND / USD per band + solar money of one hour (or a sum of hours). */
	public static final class HourMoney {
		private final Map<TouBand, BigDecimal> bandKwh = zeros();
		private final Map<TouBand, BigDecimal> bandVnd = zeros();
		private final Map<TouBand, BigDecimal> bandUsd = zeros();
		private BigDecimal solarVnd = BigDecimal.ZERO;
		private BigDecimal solarUsd = BigDecimal.ZERO;

		private static Map<TouBand, BigDecimal> zeros() {
			Map<TouBand, BigDecimal> map = new EnumMap<>(TouBand.class);

			for (TouBand band : TouBand.values()) {
				map.put(band, BigDecimal.ZERO);
			}

			return map;
		}

		public Map<TouBand, BigDecimal> bandKwh() {
			return bandKwh;
		}

		public Map<TouBand, BigDecimal> bandVnd() {
			return bandVnd;
		}

		public Map<TouBand, BigDecimal> bandUsd() {
			return bandUsd;
		}

		public BigDecimal gridVnd() {
			return bandVnd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		public BigDecimal gridUsd() {
			return bandUsd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		}

		public BigDecimal solarVnd() {
			return solarVnd;
		}

		public BigDecimal solarUsd() {
			return solarUsd;
		}
	}

	/**
	 * Grid = max(load - solar, 0) of the hour split into tariff bands and
	 * priced at the grid rate, solar priced at the solar rate. All zero when
	 * there is no tariff (pricing empty) or no version covers the hour's day.
	 */
	public static HourMoney price(HourSum sum, LocalDateTime hourAt, Optional<Pricing> pricing) {
		HourMoney money = new HourMoney();

		if (pricing.isEmpty()) {
			return money;
		}

		Pricing p = pricing.get();
		BigDecimal grid = sum.grid();

		for (TouShare share : p.shares(hourAt)) {
			money.bandKwh.merge(share.band(), grid.multiply(share.fraction()), BigDecimal::add);
			money.bandVnd.merge(share.band(), p.gridVnd(grid, share), BigDecimal::add);
			money.bandUsd.merge(share.band(), p.gridUsd(grid, share), BigDecimal::add);
			money.solarVnd = money.solarVnd.add(p.solarVnd(sum.solar, share));
			money.solarUsd = money.solarUsd.add(p.solarUsd(sum.solar, share));
		}

		return money;
	}

	/**
	 * Totals of the hours of [from, to) of a facHours / hourSums map: kWh,
	 * grid / solar money and whether some hour with energy has no tariff
	 * version (its money is then left out, priceMissing).
	 */
	public static final class Totals {
		private BigDecimal load = BigDecimal.ZERO;
		private BigDecimal solar = BigDecimal.ZERO;
		private BigDecimal grid = BigDecimal.ZERO;
		private BigDecimal gridVnd = BigDecimal.ZERO;
		private BigDecimal gridUsd = BigDecimal.ZERO;
		private BigDecimal solarVnd = BigDecimal.ZERO;
		private BigDecimal solarUsd = BigDecimal.ZERO;
		private boolean hasData;
		private boolean priceMissing;

		public BigDecimal load() {
			return load;
		}

		public BigDecimal solar() {
			return solar;
		}

		public BigDecimal grid() {
			return grid;
		}

		public BigDecimal gridVnd() {
			return gridVnd;
		}

		public BigDecimal gridUsd() {
			return gridUsd;
		}

		public BigDecimal solarVnd() {
			return solarVnd;
		}

		public BigDecimal solarUsd() {
			return solarUsd;
		}

		public boolean hasData() {
			return hasData;
		}

		public boolean priceMissing() {
			return priceMissing;
		}
	}

	public static Totals totals(
			Map<LocalDateTime, HourSum> hours,
			LocalDateTime from,
			LocalDateTime to,
			Optional<Pricing> pricing
	) {
		Totals t = new Totals();

		hours.forEach((hourAt, sum) -> {
			if (hourAt.isBefore(from) || !hourAt.isBefore(to)) {
				return;
			}

			t.load = t.load.add(sum.load);
			t.solar = t.solar.add(sum.solar);
			t.grid = t.grid.add(sum.grid());
			t.hasData |= sum.hasLoad || sum.hasSolar;

			boolean priced = pricing.isPresent() && pricing.get().priced(hourAt.toLocalDate());

			if (priced) {
				HourMoney money = price(sum, hourAt, pricing);
				t.gridVnd = t.gridVnd.add(money.gridVnd());
				t.gridUsd = t.gridUsd.add(money.gridUsd());
				t.solarVnd = t.solarVnd.add(money.solarVnd);
				t.solarUsd = t.solarUsd.add(money.solarUsd);
			} else if (sum.load.signum() != 0 || sum.solar.signum() != 0) {
				t.priceMissing = true;
			}
		});

		return t;
	}

	/** load / solar / grid kWh per day (date of the hour start), sorted. */
	public static Map<LocalDate, Totals> byDay(Map<LocalDateTime, HourSum> hours) {
		Map<LocalDate, Totals> days = new TreeMap<>();

		hours.forEach((hourAt, sum) -> {
			Totals t = days.computeIfAbsent(hourAt.toLocalDate(), key -> new Totals());
			t.load = t.load.add(sum.load);
			t.solar = t.solar.add(sum.solar);
			t.grid = t.grid.add(sum.grid());
			t.hasData |= sum.hasLoad || sum.hasSolar;
		});

		return days;
	}
}
