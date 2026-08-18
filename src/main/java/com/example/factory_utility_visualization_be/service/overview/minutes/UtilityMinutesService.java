package com.example.factory_utility_visualization_be.service.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.OverviewMinutePointDto;
import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteProjection;
import com.example.factory_utility_visualization_be.repository.overview.minutes.UtilityMinuteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilityMinutesService {

	private static final String DEFAULT_FAC = "KVH";

	private static final int DEFAULT_MINUTES = 60;

	private static final int MAX_MINUTES =
			24 * 60;

	// =========================================================
	// TIMEZONE
	// =========================================================

	private static final ZoneId VIETNAM_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");

	private static final Set<String> ALLOWED_FACS =
			Set.of(
					"KVH",
					"Fac_A",
					"Fac_B",
					"Fac_C"
			);

	private final UtilityMinuteRepo repo;

	// =========================================================
	// MINUTE DASHBOARD
	// =========================================================

	@Transactional(readOnly = true)
	public UtilityMinuteDashboardDto getMinuteDashboard(
			String facId,
			Integer minutes
	) {

		final String fac =
				normalizeFac(facId);

		final int safeMinutes =
				normalizeMinutes(minutes);

		// =====================================================
		// VIETNAM CURRENT TIME
		// =====================================================

		final LocalDateTime toTime =
				LocalDateTime.now(
						VIETNAM_ZONE
				);

		final LocalDateTime fromTime =
				toTime.minusMinutes(
						safeMinutes
				);

		// L?y thêm d? li?u tru?c fromTime
		// d? LAG() có previous value
		final LocalDateTime lagFromTime =
				fromTime.minusMinutes(10);

		// =====================================================
		// DEBUG
		// =====================================================

		System.out.println(
				"[MINUTE] FAC = " + fac
		);

		System.out.println(
				"[MINUTE] FROM = "
						+ fromTime
		);

		System.out.println(
				"[MINUTE] TO = "
						+ toTime
		);

		System.out.println(
				"[MINUTE] LAG FROM = "
						+ lagFromTime
		);

		// =====================================================
		// QUERY
		// =====================================================

		final List<UtilityMinuteProjection> rows =
				repo.findMinuteDashboard(
						fac,
						lagFromTime,
						fromTime,
						toTime
				);

		final List<OverviewMinutePointDto> electricity =
				new ArrayList<>();

		final List<OverviewMinutePointDto> water =
				new ArrayList<>();

		final List<OverviewMinutePointDto> air =
				new ArrayList<>();

		for (UtilityMinuteProjection row : rows) {

			if (row.getTs() == null ||
					row.getUtilityType() == null) {
				continue;
			}

			final Double value =
					row.getValue() == null
							? null
							: row.getValue()
							.doubleValue();

			final OverviewMinutePointDto point =
					new OverviewMinutePointDto(
							row.getTs(),
							value,
							row.getName()
					);

			final String utilityType =
					row.getUtilityType()
							.trim()
							.toUpperCase(
									Locale.ROOT
							);

			switch (utilityType) {

				case "ELECTRICITY" ->
						electricity.add(point);

				case "WATER" ->
						water.add(point);

				case "AIR" ->
						air.add(point);

				default -> {
				}
			}
		}

		return new UtilityMinuteDashboardDto(
				fac,
				safeMinutes,

				// gi? Vi?t Nam
				toTime,

				List.copyOf(
						electricity
				),

				List.copyOf(
						water
				),

				List.copyOf(
						air
				)
		);
	}

	// =========================================================
	// OLD API
	// =========================================================

	@Transactional(readOnly = true)
	public List<OverviewMinutePointDto> getUtilityPerMinute(
			String facId,
			Integer minutes,
			String type
	) {

		final String normalizedType =
				normalizeType(type);

		if (normalizedType == null) {
			return List.of();
		}

		final UtilityMinuteDashboardDto dashboard =
				getMinuteDashboard(
						facId,
						minutes
				);

		return switch (normalizedType) {

			case "ELECTRICITY" ->
					dashboard.electricity();

			case "WATER" ->
					dashboard.water();

			case "AIR" ->
					dashboard.air();

			default ->
					List.of();
		};
	}

	// =========================================================
	// NORMALIZE FAC
	// =========================================================

	private String normalizeFac(
			String facId
	) {

		if (facId == null ||
				facId.isBlank()) {

			return DEFAULT_FAC;
		}

		final String normalized =
				facId.trim();

		if (!ALLOWED_FACS.contains(
				normalized
		)) {

			throw new IllegalArgumentException(
					"Invalid facId: "
							+ normalized
			);
		}

		return normalized;
	}

	// =========================================================
	// NORMALIZE MINUTES
	// =========================================================

	private int normalizeMinutes(
			Integer minutes
	) {

		if (minutes == null ||
				minutes <= 0) {

			return DEFAULT_MINUTES;
		}

		return Math.min(
				minutes,
				MAX_MINUTES
		);
	}

	// =========================================================
	// NORMALIZE TYPE
	// =========================================================

	private String normalizeType(
			String type
	) {

		if (type == null ||
				type.isBlank()) {

			return null;
		}

		final String normalized =
				type.trim()
						.toUpperCase(
								Locale.ROOT
						);

		return switch (normalized) {

			case "ELECTRICITY",
			     "WATER",
			     "AIR" ->
					normalized;

			default ->
					null;
		};
	}
}