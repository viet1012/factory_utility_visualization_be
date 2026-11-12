package com.example.factory_utility_visualization_be.service.runtime;

import com.example.factory_utility_visualization_be.repository.runtime.PowerUsageRepo;
import com.example.factory_utility_visualization_be.repository.runtime.projection.PowerUsageTariffProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Time-of-use tariff from F2_Utility_Cost_Master, shared by
 * /api/utility/power-usage, /api/utility/electric-consumption and solar.
 *
 * The table holds several versions: rows with the same
 * [effective_from, effective_to) (DATE, to exclusive, NULL = still in effect)
 * form one tariff, and an hour is priced with the version in effect on the
 * date of that hour. A date without a version (or whose version cannot be
 * mapped, see below) has no price: shares() is empty for it.
 *
 * Per version, WD ('1' = Sunday, '2-7' = Monday..Saturday) and hour of day,
 * the tariff rows covering that hour, weighted by overlap (overnight rows:
 * frTime > toTime). This is the only reader of the table (via
 * PowerUsageRepo.findTariffs); no SQL query prices energy. The table has no band column, so bands come from the 3 distinct
 * VND rates of the version (highest = HIGH_PEAK, lowest = LOW_PEAK). A
 * version is unusable when its rates cannot be mapped to 3 bands or an hour
 * has no rate.
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

	/**
	 * One tariff version, valid on [effectiveFrom, effectiveTo).
	 *
	 * @param effectiveFrom inclusive, null = no lower bound
	 * @param effectiveTo   exclusive, null = still in effect
	 */
	public record TouVersion(
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			Map<String, List<List<TouShare>>> byWd
	) {
		public boolean covers(LocalDate day) {
			return (effectiveFrom == null || !day.isBefore(effectiveFrom))
					&& (effectiveTo == null || day.isBefore(effectiveTo));
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

	public static final class TouTariff {
		// usable versions, latest effectiveFrom first
		private final List<TouVersion> versions;

		private TouTariff(List<TouVersion> versions) {
			this.versions = versions;
		}

		// version in effect on day (latest effectiveFrom when versions overlap)
		public Optional<TouVersion> version(LocalDate day) {
			return versions.stream().filter(v -> v.covers(day)).findFirst();
		}

		// shares of the hour starting at hourAt; empty = no price for that day
		public List<TouShare> shares(LocalDateTime hourAt) {
			return version(hourAt.toLocalDate()).map(v -> v.shares(hourAt)).orElse(List.of());
		}

		// VND/kWh of a band on a Monday..Saturday of the version in effect on day
		public Optional<BigDecimal> weekdayRate(TouBand band, LocalDate day) {
			return version(day).flatMap(v -> v.weekdayRate(band));
		}
	}

	private record VersionKey(LocalDate from, LocalDate to) {
	}

	// empty when no version is usable
	public Optional<TouTariff> load() {
		Map<VersionKey, List<PowerUsageTariffProjection>> byVersion = new LinkedHashMap<>();

		for (PowerUsageTariffProjection row : repo.findTariffs()) {
			if (row.getWd() == null || row.getFrTime() == null || row.getToTime() == null || row.getVnd() == null) {
				continue;
			}

			VersionKey key = new VersionKey(
					row.getEffectiveFrom() == null ? null : row.getEffectiveFrom().toLocalDate(),
					row.getEffectiveTo() == null ? null : row.getEffectiveTo().toLocalDate());
			byVersion.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
		}

		List<TouVersion> versions = new ArrayList<>();

		byVersion.forEach((key, rows) -> version(rows)
				.map(byWd -> new TouVersion(key.from(), key.to(), byWd))
				.ifPresent(versions::add));

		if (versions.isEmpty()) {
			return Optional.empty();
		}

		// latest effectiveFrom first (null = oldest)
		versions.sort(Comparator.comparing(TouVersion::effectiveFrom,
				Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder())).reversed());

		return Optional.of(new TouTariff(List.copyOf(versions)));
	}

	// WD -> hour -> shares of one version; empty when not mappable
	private Optional<Map<String, List<List<TouShare>>>> version(List<PowerUsageTariffProjection> rows) {
		TreeSet<BigDecimal> rates = new TreeSet<>(Comparator.naturalOrder());
		rows.stream().map(PowerUsageTariffProjection::getVnd).filter(Objects::nonNull).forEach(rates::add);

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

		return Optional.of(tariff);
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
