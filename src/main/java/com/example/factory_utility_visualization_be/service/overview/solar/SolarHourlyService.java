package com.example.factory_utility_visualization_be.service.overview.solar;

import com.example.factory_utility_visualization_be.cache_config.UtilityCacheNames;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarHourKwhDto;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarHourlyDto;
import com.example.factory_utility_visualization_be.dto.overview.solar.detail.SolarHourlyFacilityDto;
import com.example.factory_utility_visualization_be.exception.SolarHourlyBadRequestException;
import com.example.factory_utility_visualization_be.model.F2UtilityScada;
import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.repository.overview.solar.SolarDashboardRepo;
import com.example.factory_utility_visualization_be.repository.overview.solar.projection.SolarHourlyProfileProjection;
import com.example.factory_utility_visualization_be.service.runtime.UtilityMasterDataCacheService;
import com.example.factory_utility_visualization_be.service.util.FacilityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * GET /api/solar/hourly — solar kWh theo giờ của một ngày, tách theo từng nhà máy.
 *
 * Ghi chú từ SQL SolarDashboardRepo.getSolarHourlyProfile + FacilityValidator:
 * - fac = "KVH": điều kiện {@code UPPER(:fac) = 'KVH'} bỏ qua lọc sc.fac, nên KVH = MỌI channel
 *   có box_id = 'SOLAR' (của bất kỳ fac nào, kể cả fac null/ngoài Fac_A/B/C). Fac cụ thể:
 *   {@code UPPER(sc.fac) = UPPER(:fac)}. Chỉ lấy dòng History_Main MTD = 'MTD', value > 0,
 *   param name_en = "Total Energy Consumption", nhóm theo DATEPART(HOUR, pick_at).
 * - Giờ không có dữ liệu: query KHÔNG trả dòng (không có bảng Hours như getSolarMonthlyCost),
 *   nên service tự lấp đủ 0..23 (0 nếu giờ đã kết thúc, null nếu là giờ hiện tại/tương lai của hôm nay).
 * - Lỗi 400 (date sai định dạng/tương lai: SolarHourlyBadRequestException; facId sai:
 *   InvalidFacilityException) đi qua GlobalExceptionHandler.
 * - FacilityValidator chỉ có danh sách fac hợp lệ (KVH, Fac_A, Fac_B, Fac_C), không biết fac nào có
 *   solar. Danh sách fac có solar lấy từ master data (UtilityMasterDataCacheService): channel có
 *   box_id = 'SOLAR' -> scada_id -> F2_Utility_Scada.fac — cùng phép join như SQL, không hardcode.
 *   Channel solar có fac null/rỗng sẽ không thuộc fac nào nên tổng các fac có thể thấp hơn KVH
 *   của query; khi bật DEBUG, service so với getSolarHourlyProfile("KVH") và WARN nếu lệch > 0.5%.
 * - Ngày đã qua được cache theo (facId, date) trong SOLAR_HOURLY_HISTORY; hôm nay không cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolarHourlyService {

	private static final String ENERGY_NAME =
			"Total Energy Consumption";

	private static final String SOLAR_BOX_ID =
			"SOLAR";

	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private static final BigDecimal MISMATCH_RATIO =
			new BigDecimal("0.005");

	private final SolarDashboardRepo repo;
	private final UtilityMasterDataCacheService masterData;
	private final CacheManager cacheManager;

	@Transactional(readOnly = true)
	public SolarHourlyDto getHourly(
			String facId,
			String date
	) {

		final String fac =
				FacilityValidator.normalizeOptionalWithDefault(facId);

		final LocalDateTime now =
				LocalDateTime.now(APP_ZONE);

		final LocalDate day =
				parseDate(date, now.toLocalDate());

		if (day.isBefore(now.toLocalDate())) {
			final Cache cache =
					cacheManager.getCache(UtilityCacheNames.SOLAR_HOURLY_HISTORY);

			if (cache != null) {
				return cache.get(
						fac + "_" + day,
						() -> build(fac, day, now)
				);
			}
		}

		return build(fac, day, now);
	}


	private SolarHourlyDto build(
			String fac,
			LocalDate day,
			LocalDateTime now
	) {

		final LocalDateTime dayStart =
				day.atStartOfDay();

		final LocalDateTime nextDayStart =
				dayStart.plusDays(1);

		// Giờ > lastPastHour chưa kết thúc (chỉ với hôm nay: giờ hiện tại + giờ tương lai):
		// không có dòng -> null thay vì 0, để chart không hiện như sản lượng tụt về 0.
		// Giờ hiện tại đã có dòng vẫn trả giá trị đang tích lũy.
		final int lastPastHour =
				day.equals(now.toLocalDate())
						? now.getHour() - 1
						: 23;

		final List<String> facs =
				FacilityValidator.DEFAULT_FAC.equals(fac)
						? solarFacilities()
						: List.of(fac);

		final List<SolarHourlyFacilityDto> facilities =
				new ArrayList<>();

		final BigDecimal[] totals =
				new BigDecimal[24];

		for (String f : facs) {
			final Map<Integer, BigDecimal> rows =
					toHourMap(
							repo.getSolarHourlyProfile(
									f,
									dayStart,
									nextDayStart,
									ENERGY_NAME
							)
					);

			final List<SolarHourKwhDto> hours =
					fillHours(rows, lastPastHour);

			for (SolarHourKwhDto h : hours) {
				if (h.kwh() != null) {
					totals[h.hour()] =
							totals[h.hour()] == null
									? h.kwh()
									: totals[h.hour()].add(h.kwh());
				}
			}

			facilities.add(toFacilityDto(f, hours));
		}

		final List<SolarHourKwhDto> totalHours =
				new ArrayList<>(24);

		for (int hour = 0; hour < 24; hour++) {
			totalHours.add(
					new SolarHourKwhDto(
							hour,
							totals[hour] != null
									? totals[hour]
									: hour <= lastPastHour ? BigDecimal.ZERO : null
					)
			);
		}

		if (log.isDebugEnabled() && FacilityValidator.DEFAULT_FAC.equals(fac)) {
			compareWithKvhQuery(day, dayStart, nextDayStart, totalHours);
		}

		final SolarHourlyFacilityDto overall =
				toFacilityDto(fac, totalHours);

		return new SolarHourlyDto(
				day,
				fac,
				List.copyOf(facilities),
				List.copyOf(totalHours),
				overall.totalKwh(),
				overall.peakKwh(),
				overall.peakHour()
		);
	}


	/**
	 * Fac có ít nhất một channel box_id = 'SOLAR', theo đúng join ch.scada_id = sc.scada_id của SQL.
	 * Tên fac được chuẩn hóa qua FacilityValidator nếu là fac đã biết; fac lạ giữ nguyên (SQL so UPPER).
	 */
	private List<String> solarFacilities() {

		final Set<String> solarScadaIds =
				masterData.findAllChannels()
						.stream()
						.filter(ch -> ch.getBoxId() != null
								&& SOLAR_BOX_ID.equalsIgnoreCase(ch.getBoxId().trim()))
						.map(F2UtilityScadaChannel::getScadaId)
						.filter(Objects::nonNull)
						.collect(Collectors.toSet());

		final Map<String, String> byUpper =
				new TreeMap<>();

		for (F2UtilityScada sc : masterData.findAllScadas()) {
			if (sc.getFac() == null
					|| sc.getFac().isBlank()
					|| !solarScadaIds.contains(sc.getScadaId())) {
				continue;
			}

			final String raw =
					sc.getFac().trim();

			byUpper.putIfAbsent(
					raw.toUpperCase(Locale.ROOT),
					canonicalOrRaw(raw)
			);
		}

		return List.copyOf(byUpper.values());
	}


	private String canonicalOrRaw(
			String raw
	) {
		try {
			return FacilityValidator.normalizeRequired(raw, "fac");
		} catch (RuntimeException ex) {
			return raw;
		}
	}


	private void compareWithKvhQuery(
			LocalDate day,
			LocalDateTime dayStart,
			LocalDateTime nextDayStart,
			List<SolarHourKwhDto> totalHours
	) {

		final Map<Integer, BigDecimal> kvh =
				toHourMap(
						repo.getSolarHourlyProfile(
								FacilityValidator.DEFAULT_FAC,
								dayStart,
								nextDayStart,
								ENERGY_NAME
						)
				);

		final List<String> mismatches =
				new ArrayList<>();

		for (SolarHourKwhDto h : totalHours) {
			final BigDecimal summed =
					h.kwh() == null ? BigDecimal.ZERO : h.kwh();

			final BigDecimal expected =
					kvh.getOrDefault(h.hour(), BigDecimal.ZERO);

			if (exceedsTolerance(summed, expected)) {
				mismatches.add(h.hour() + "h: facs=" + summed + " kvh=" + expected);
			}
		}

		if (!mismatches.isEmpty()) {
			log.warn(
					"Solar hourly {}: sum of per-fac profiles differs from KVH query by > 0.5% at {}",
					day,
					mismatches
			);
		}
	}


	private boolean exceedsTolerance(
			BigDecimal actual,
			BigDecimal expected
	) {

		final BigDecimal diff =
				actual.subtract(expected).abs();

		if (expected.signum() == 0) {
			return diff.signum() != 0;
		}

		return diff.divide(expected.abs(), 6, RoundingMode.HALF_UP)
				.compareTo(MISMATCH_RATIO) > 0;
	}


	private Map<Integer, BigDecimal> toHourMap(
			List<SolarHourlyProfileProjection> rows
	) {

		final Map<Integer, BigDecimal> map =
				new HashMap<>();

		for (SolarHourlyProfileProjection row : rows) {
			if (row.getScaleHour() != null && row.getEnergyKwh() != null) {
				map.merge(row.getScaleHour(), row.getEnergyKwh(), BigDecimal::add);
			}
		}

		return map;
	}


	private List<SolarHourKwhDto> fillHours(
			Map<Integer, BigDecimal> rows,
			int lastPastHour
	) {

		final List<SolarHourKwhDto> hours =
				new ArrayList<>(24);

		for (int hour = 0; hour < 24; hour++) {
			final BigDecimal kwh =
					rows.get(hour);

			hours.add(
					new SolarHourKwhDto(
							hour,
							kwh != null
									? kwh
									: hour <= lastPastHour ? BigDecimal.ZERO : null
					)
			);
		}

		return List.copyOf(hours);
	}


	private SolarHourlyFacilityDto toFacilityDto(
			String fac,
			List<SolarHourKwhDto> hours
	) {

		BigDecimal total =
				BigDecimal.ZERO;

		BigDecimal peak =
				BigDecimal.ZERO;

		Integer peakHour =
				null;

		for (SolarHourKwhDto h : hours) {
			if (h.kwh() == null) {
				continue;
			}

			total = total.add(h.kwh());

			if (h.kwh().compareTo(peak) > 0) {
				peak = h.kwh();
				peakHour = h.hour();
			}
		}

		return new SolarHourlyFacilityDto(
				fac,
				total,
				peak,
				peakHour,
				hours
		);
	}


	private LocalDate parseDate(
			String date,
			LocalDate today
	) {

		if (date == null || date.isBlank()) {
			return today;
		}

		final LocalDate parsed;

		try {
			parsed = LocalDate.parse(date.trim());
		} catch (DateTimeParseException ex) {
			throw new SolarHourlyBadRequestException(
					"date must be yyyy-MM-dd: " + date
			);
		}

		if (parsed.isAfter(today)) {
			throw new SolarHourlyBadRequestException(
					"date must not be in the future: " + date
			);
		}

		return parsed;
	}
}
