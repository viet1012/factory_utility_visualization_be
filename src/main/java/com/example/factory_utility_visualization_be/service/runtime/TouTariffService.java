package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Time-of-use tariff from F2_Utility_Cost_Master, shared by
 * /api/utility/power-usage and /api/utility/electric-consumption.
 *
 * Per WD ('1' = Sunday, '2-7' = Monday..Saturday) and hour of day, the tariff
 * rows covering that hour, weighted by overlap (overnight rows: frTime >
 * toTime), like the FinalRate CTE of the monthly / hourly cost queries. The
 * table has no band column, so bands come from the 3 distinct VND rates
 * (highest = HIGH_PEAK, lowest = LOW_PEAK). Empty when the rates cannot be
 * mapped to 3 bands or an hour has no rate.
 */
@Service
@RequiredArgsConstructor
public class TouTariffService {

	// F2_Utility_Cost_Master.WD
	private static final String WD_SUNDAY = "1";
	private static final String WD_OTHER = "2-7";

	private static final int HOURS_PER_DAY = 24;

	private final PowerUsageRepo repo;

	public enum TouBand {
		NORMAL,
		HIGH_PEAK,
		LOW_PEAK
	}

	// part of one hour of day billed at vnd; fractions of an hour sum to 1
	public record TouShare(TouBand band, BigDecimal vnd, BigDecimal fraction) {
	}

	public static final class TouTariff {
		private final Map<String, List<List<TouShare>>> byWd;

		private TouTariff(Map<String, List<List<TouShare>>> byWd) {
			this.byWd = byWd;
		}

		// shares of the hour starting at hourAt
		public List<TouShare> shares(LocalDateTime hourAt) {
			String wd = hourAt.getDayOfWeek() == DayOfWeek.SUNDAY ? WD_SUNDAY : WD_OTHER;
			return byWd.get(wd).get(hourAt.getHour());
		}

		// VND/kWh of a band on a Monday..Saturday (first matching row)
		public Optional<BigDecimal> weekdayRate(TouBand band) {
			return byWd.get(WD_OTHER).stream()
					.flatMap(List::stream)
					.filter(share -> share.band() == band)
					.map(TouShare::vnd)
					.findFirst();
		}
	}

	public Optional<TouTariff> load() {
		List<PowerUsageTariffProjection> rows = repo.findTariffs().stream()
				.filter(row -> row.getWd() != null && row.getFrTime() != null
						&& row.getToTime() != null && row.getVnd() != null)
				.toList();

		TreeSet<BigDecimal> rates = new TreeSet<>(Comparator.naturalOrder());
		rows.forEach(row -> rates.add(row.getVnd()));

		if (rates.size() != 3) {
			return Optional.empty();
		}

		Map<String, List<List<TouShare>>> tariff = new HashMap<>();

		for (String wd : List.of(WD_SUNDAY, WD_OTHER)) {
			List<List<TouShare>> byHour = new ArrayList<>(HOURS_PER_DAY);

			for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
				List<TouShare> shares = new ArrayList<>();
				BigDecimal covered = BigDecimal.ZERO;

				for (PowerUsageTariffProjection row : rows) {
					if (!wd.equals(row.getWd().trim())) {
						continue;
					}

					BigDecimal overlap = overlap(row.getFrTime(), row.getToTime(), hour);

					if (overlap.signum() > 0) {
						shares.add(new TouShare(band(row.getVnd(), rates), row.getVnd(), overlap));
						covered = covered.add(overlap);
					}
				}

				if (covered.signum() == 0) {
					return Optional.empty();
				}

				BigDecimal total = covered;
				byHour.add(shares.stream()
						.map(share -> new TouShare(
								share.band(),
								share.vnd(),
								share.fraction().divide(total, 8, RoundingMode.HALF_UP)))
						.toList());
			}

			tariff.put(wd, byHour);
		}

		return Optional.of(new TouTariff(tariff));
	}

	// hours of [frTime, toTime) inside [hour, hour + 1)
	private BigDecimal overlap(BigDecimal fr, BigDecimal to, int hour) {
		BigDecimal hourStart = BigDecimal.valueOf(hour);
		BigDecimal hourEnd = BigDecimal.valueOf(hour + 1L);

		if (fr.compareTo(to) < 0) {
			return positive(to.min(hourEnd).subtract(fr.max(hourStart)));
		}

		if (fr.compareTo(to) > 0) {
			return positive(hourEnd.subtract(fr.max(hourStart)))
					.add(positive(to.min(hourEnd).subtract(hourStart)));
		}

		return BigDecimal.ZERO;
	}

	private TouBand band(BigDecimal vnd, TreeSet<BigDecimal> rates) {
		if (vnd.compareTo(rates.first()) == 0) {
			return TouBand.LOW_PEAK;
		}

		return vnd.compareTo(rates.last()) == 0 ? TouBand.HIGH_PEAK : TouBand.NORMAL;
	}

	private BigDecimal positive(BigDecimal value) {
		return value.signum() < 0 ? BigDecimal.ZERO : value;
	}
}
