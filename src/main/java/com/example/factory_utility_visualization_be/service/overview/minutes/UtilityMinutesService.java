package com.example.factory_utility_visualization_be.service.overview.minutes;

import com.example.factory_utility_visualization_be.dto.overview.minutes.OverviewMinutePointDto;
import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.minutes.UtilityMinuteProjection;
import com.example.factory_utility_visualization_be.repository.overview.minutes.UtilityMinuteRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UtilityMinutesService {

	private static final String DEFAULT_FAC = "KVH";
	private static final int DEFAULT_MINUTES = 60;
	private static final int MAX_MINUTES = 24 * 60;

	private static final Set<String> ALLOWED_FACS =
			Set.of(
					"KVH",
					"Fac_A",
					"Fac_B",
					"Fac_C"
			);

	private final UtilityMinuteRepo repo;

	@Transactional(readOnly = true)
	public UtilityMinuteDashboardDto getMinuteDashboard(
			String facId,
			Integer minutes
	) {
		final String fac = normalizeFac(facId);
		final int safeMinutes = normalizeMinutes(minutes);

		final LocalDateTime toTime =
				LocalDateTime.now();

		final LocalDateTime fromTime =
				toTime.minusMinutes(safeMinutes);

		/*
		 * Lấy thêm dữ liệu trước fromTime để LAG có previous value.
		 * Nếu dữ liệu ghi mỗi vài phút thì buffer 10 phút an toàn hơn 1 phút.
		 */
		final LocalDateTime lagFromTime =
				fromTime.minusMinutes(10);

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
							: row.getValue().doubleValue();

			final OverviewMinutePointDto point =
					new OverviewMinutePointDto(
							row.getTs(),
							value,
							row.getName()
					);

			final String utilityType =
					row.getUtilityType()
							.trim()
							.toUpperCase(Locale.ROOT);

			switch (utilityType) {
				case "ELECTRICITY" ->
						electricity.add(point);

				case "WATER" ->
						water.add(point);

				case "AIR" ->
						air.add(point);

				default -> {
					// Ignore unknown utility type.
				}
			}
		}

		return new UtilityMinuteDashboardDto(
				fac,
				safeMinutes,
				toTime,
				List.copyOf(electricity),
				List.copyOf(water),
				List.copyOf(air)
		);
	}

	/*
	 * Giữ method cũ tạm thời để tránh làm hỏng client cũ.
	 * Tuy nhiên method này gọi batch query rồi chọn một list.
	 */
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

	private String normalizeFac(String facId) {
		if (facId == null || facId.isBlank()) {
			return DEFAULT_FAC;
		}

		final String normalized =
				facId.trim();

		if (!ALLOWED_FACS.contains(normalized)) {
			throw new IllegalArgumentException(
					"Invalid facId: " + normalized
			);
		}

		return normalized;
	}

	private int normalizeMinutes(Integer minutes) {
		if (minutes == null || minutes <= 0) {
			return DEFAULT_MINUTES;
		}

		return Math.min(
				minutes,
				MAX_MINUTES
		);
	}

	private String normalizeType(String type) {
		if (type == null || type.isBlank()) {
			return null;
		}

		final String normalized =
				type.trim().toUpperCase(Locale.ROOT);

		return switch (normalized) {
			case "ELECTRICITY", "WATER", "AIR" ->
					normalized;

			default ->
					null;
		};
	}
}