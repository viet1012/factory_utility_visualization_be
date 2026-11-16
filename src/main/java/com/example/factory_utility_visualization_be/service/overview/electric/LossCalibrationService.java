package com.example.factory_utility_visualization_be.service.overview.electric;

import com.example.factory_utility_visualization_be.dto.overview.electric.LossCalibrationResultDto;
import com.example.factory_utility_visualization_be.dto.overview.electric.LossCalibrationResultDto.Excluded;
import com.example.factory_utility_visualization_be.dto.overview.electric.LossCalibrationResultDto.Sample;
import com.example.factory_utility_visualization_be.exception.EvnCalibrationBadRequestException;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository;
import com.example.factory_utility_visualization_be.repository.overview.electric.EvnCalibrationJdbcRepository.EvnDay;
import com.example.factory_utility_visualization_be.service.overview.electric.ElectricConsumptionCalculator.MeasuredDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * POST /api/utility/loss-calibration/recompute (run by hand):
 *
 *   loss_kwh_per_hour = median over sample days of (evn_total - measured_grid) / 24
 *
 * sample day = day of [until - days, until) with an F2_Utility_Evn_Daily row
 * of the scope AND full meter coverage (every configured grid meter of the
 * scope has 24 valid, non-outlier hours; a partial day is never used).
 * measured_grid = sum over hours of max(load - solar, 0), same as
 * totalElectricity of GET /electric-consumption.
 *
 * Scopes FAC_AB / FAC_C only: KVH -> 400, calibrated KVH = calibrated
 * FAC_AB + calibrated FAC_C per hour (ElectricConsumptionCalculator).
 * Stored even when suspicious, with warnings (result + log WARN):
 * loss < 0 (measured > EVN, likely a meter counted twice) and
 * |loss| > 30 % of the average hourly load of the sample days.
 *
 * Appends one F2_Utility_Loss_Calibration row (effective_from = first
 * sample day, effective_to = NULL); older rows are never changed: a day uses
 * the newest row covering it. No sample day -> HTTP 400, nothing stored.
 * Measured data is never written.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LossCalibrationService {

	private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	// KVH is never calibrated on its own (= FAC_AB + FAC_C per hour, see calculator)
	private static final Set<String> SCOPES = Set.of("FAC_AB", "FAC_C");

	// warning when |loss| > this share of the average hourly load of the scope
	private static final BigDecimal MAX_LOSS_SHARE = new BigDecimal("0.30");

	private static final int MAX_DAYS = 366;

	private static final BigDecimal HOURS = BigDecimal.valueOf(24);

	private final ElectricConsumptionCalculator calculator;
	private final EvnCalibrationJdbcRepository repo;
	private final ElectricConsumptionCacheEvictor cacheEvictor;

	@Transactional
	public LossCalibrationResultDto recompute(String scopeParam, Integer daysParam, String untilParam, String createdBy) {
		String scope = scopeParam == null ? "" : scopeParam.trim().toUpperCase(Locale.ROOT);

		if ("KVH".equals(scope)) {
			throw new EvnCalibrationBadRequestException(
					"KVH tính từ FAC_AB + FAC_C: recompute scope=FAC_AB và scope=FAC_C");
		}

		if (!SCOPES.contains(scope)) {
			throw new EvnCalibrationBadRequestException("scope must be FAC_AB or FAC_C");
		}

		int days = daysParam == null ? 30 : daysParam;

		if (days < 1 || days > MAX_DAYS) {
			throw new EvnCalibrationBadRequestException("days must be between 1 and " + MAX_DAYS);
		}

		LocalDate today = LocalDate.now(APP_ZONE);
		LocalDate until = parseDay(untilParam, today);

		if (until.isAfter(today)) {
			throw new EvnCalibrationBadRequestException("until must not be after today (" + today + ")");
		}

		LocalDate from = until.minusDays(days);
		int hourOffset = calculator.defaultHourOffset();

		List<EvnDay> evnDays = repo.findEvnDays(from, until, scope);
		Map<LocalDate, MeasuredDay> measured = calculator.measuredDays(scope, from, until, hourOffset);

		List<Sample> samples = new ArrayList<>();
		List<Excluded> excluded = new ArrayList<>();
		List<BigDecimal> diffs = new ArrayList<>();
		BigDecimal sampleLoad = BigDecimal.ZERO;

		for (EvnDay evn : evnDays) {
			MeasuredDay day = measured.get(evn.day());

			if (day == null) {
				excluded.add(new Excluded(evn.day().toString(), "NO_MEASURED_DATA", null));
				continue;
			}

			if (!day.fullCoverage()) {
				excluded.add(new Excluded(evn.day().toString(), "COVERAGE_BELOW_100", day.coveragePct()));
				continue;
			}

			BigDecimal diff = evn.totalKwh().subtract(day.gridKwh()).divide(HOURS, 4, RoundingMode.HALF_UP);
			diffs.add(diff);
			sampleLoad = sampleLoad.add(day.loadKwh());
			samples.add(new Sample(evn.day().toString(), evn.totalKwh(), day.gridKwh().setScale(4, RoundingMode.HALF_UP),
					day.loadKwh().setScale(4, RoundingMode.HALF_UP), diff));
		}

		if (samples.isEmpty()) {
			throw new EvnCalibrationBadRequestException(String.format(Locale.ROOT,
					"no sample day for %s in [%s, %s): %d EVN days, none with 100%% meter coverage "
							+ "(%d without measured data, %d below 100%%); nothing stored",
					scope, from, until, evnDays.size(),
					excluded.stream().filter(e -> "NO_MEASURED_DATA".equals(e.reason())).count(),
					excluded.stream().filter(e -> "COVERAGE_BELOW_100".equals(e.reason())).count()));
		}

		BigDecimal loss = median(diffs);
		LocalDate effectiveFrom = LocalDate.parse(samples.get(0).day());
		LocalDate lastSample = LocalDate.parse(samples.get(samples.size() - 1).day());
		String method = truncate(String.format(Locale.ROOT,
				"median((evn_total-measured_grid)/24) n=%d full-coverage days %s..%s window %dd until %s hourOffset %d",
				samples.size(), effectiveFrom, lastSample, days, until, hourOffset), 200);
		LocalDateTime now = LocalDateTime.now(APP_ZONE).truncatedTo(ChronoUnit.SECONDS);
		String by = createdBy == null || createdBy.isBlank() ? null : truncate(createdBy.trim(), 100);

		// full-coverage sample days = 24 h each
		BigDecimal avgLoad = sampleLoad.divide(HOURS.multiply(BigDecimal.valueOf(samples.size())), 4,
				RoundingMode.HALF_UP);
		BigDecimal lossPct = avgLoad.signum() == 0 ? null
				: loss.abs().multiply(BigDecimal.valueOf(100)).divide(avgLoad, 1, RoundingMode.HALF_UP);
		List<String> warnings = new ArrayList<>();

		if (loss.signum() < 0) {
			warnings.add(String.format(Locale.ROOT,
					"loss_kwh_per_hour = %s < 0: số đo lớn hơn EVN (median), dấu hiệu cộng trùng meter "
							+ "(kiểm tra excluded-meters / meter-scope của %s); calibrated sẽ THẤP hơn số đo",
					loss.toPlainString(), scope));
		}

		if (avgLoad.signum() > 0 && loss.abs().compareTo(avgLoad.multiply(MAX_LOSS_SHARE)) > 0) {
			warnings.add(String.format(Locale.ROOT,
					"|loss_kwh_per_hour| = %s = %s%% tải trung bình giờ (%s kWh/h) của %s, vượt 30%%: "
							+ "kiểm tra meter thiếu / cộng trùng hoặc số EVN trước khi dùng",
					loss.abs().toPlainString(), lossPct.toPlainString(), avgLoad.toPlainString(), scope));
		}

		long id = repo.insertCalibration(scope, loss, effectiveFrom, null, method, now, by);
		cacheEvictor.evictAfterCommit("loss calibration " + scope + " id " + id);

		log.info("loss calibration {} id {}: {} kWh/h from {} sample days", scope, id, loss, samples.size());
		warnings.forEach(w -> log.warn("loss calibration {} id {}: {}", scope, id, w));

		return new LossCalibrationResultDto(
				id,
				scope,
				loss,
				loss.multiply(HOURS).setScale(4, RoundingMode.HALF_UP),
				effectiveFrom.toString(),
				null,
				method,
				now.atZone(APP_ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
				by,
				from.toString(),
				until.toString(),
				hourOffset,
				samples.size(),
				excluded.size(),
				diffs.stream().min(BigDecimal::compareTo).orElse(null),
				diffs.stream().max(BigDecimal::compareTo).orElse(null),
				avgLoad,
				lossPct,
				warnings,
				samples,
				excluded
		);
	}

	// middle value; even count -> mean of the two middle values
	static BigDecimal median(List<BigDecimal> values) {
		List<BigDecimal> sorted = values.stream().sorted().toList();
		int n = sorted.size();

		if (n % 2 == 1) {
			return sorted.get(n / 2).setScale(4, RoundingMode.HALF_UP);
		}

		return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
	}

	private static LocalDate parseDay(String value, LocalDate defaultDay) {
		if (value == null || value.isBlank()) {
			return defaultDay;
		}

		try {
			return LocalDate.parse(value.trim());
		} catch (DateTimeParseException e) {
			throw new EvnCalibrationBadRequestException("until must be yyyy-MM-dd: '" + value.trim() + "'");
		}
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
