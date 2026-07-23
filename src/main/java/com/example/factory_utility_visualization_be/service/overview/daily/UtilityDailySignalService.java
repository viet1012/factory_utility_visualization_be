package com.example.factory_utility_visualization_be.service.overview.daily;

import com.example.factory_utility_visualization_be.dto.overview.daily.DailySignalPointDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.DailySignalSeriesDto;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailyDashboardResponse;
import com.example.factory_utility_visualization_be.dto.overview.daily.UtilityDailySignalProjection;
import com.example.factory_utility_visualization_be.repository.overview.daily.UtilityDailyRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
public class UtilityDailySignalService {

	private final UtilityDailyRepo repository;

	public UtilityDailyDashboardResponse getDailySignals(
			String boxDeviceId,
			String month
	) {
		if (boxDeviceId == null || boxDeviceId.isBlank()) {
			throw new IllegalArgumentException(
					"boxDeviceId must not be empty"
			);
		}

		YearMonth yearMonth = YearMonth.parse(
				month,
				DateTimeFormatter.ofPattern("yyyyMM")
		);

		LocalDateTime fromTime = yearMonth
				.atDay(1)
				.atStartOfDay();

		LocalDateTime toTime = yearMonth
				.plusMonths(1)
				.atDay(1)
				.atStartOfDay();

		List<UtilityDailySignalProjection> rows =
				repository.getDailySignals(
						boxDeviceId.trim(),
						fromTime,
						toTime
				);

		Map<String, List<UtilityDailySignalProjection>> grouped =
				rows.stream().collect(
						Collectors.groupingBy(
								row -> String.join(
										"|",
										safe(row.getPlcAddress()),
										safe(row.getNameEn()),
										safe(row.getUnit())
								),
								LinkedHashMap::new,
								Collectors.toList()
						)
				);

		List<DailySignalSeriesDto> series = new ArrayList<>();

		for (List<UtilityDailySignalProjection> values
				: grouped.values()) {

			UtilityDailySignalProjection first = values.get(0);

			boolean energy =
					"Total Energy Consumption"
							.equalsIgnoreCase(first.getNameEn());

			List<DailySignalPointDto> points =
					values.stream()
							.map(row -> new DailySignalPointDto(
									row.getRecordDate(),
									row.getAvgValue(),
									row.getMinValue(),
									row.getMaxValue(),
									row.getFirstValue(),
									row.getLastValue(),
									row.getConsumption(),
									row.getSampleCount()
							))
							.toList();

			series.add(
					new DailySignalSeriesDto(
							resolveUtilityType(first.getNameEn()),
							first.getPlcAddress(),
							first.getNameEn(),
							first.getUnit(),
							energy
									? "DAILY_DELTA"
									: "AVG_MIN_MAX_LAST",
							points
					)
			);
		}

		return new UtilityDailyDashboardResponse(
				boxDeviceId.trim(),
				month,
				fromTime,
				toTime,
				series
		);
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private String resolveUtilityType(String nameEn) {
		if (nameEn == null) {
			return "OTHER";
		}

		String value = nameEn.trim().toUpperCase();

		if (value.startsWith("VOLTAGE")
				|| value.startsWith("CURRENT")
				|| value.equals("TOTAL POWER")
				|| value.equals("TOTAL ENERGY CONSUMPTION")
				|| value.contains("POWER FACTOR")) {
			return "ELECTRICITY";
		}

		if (value.contains("COMPRESSED AIR")) {
			return "AIR";
		}

		if (value.contains("COOLING TANK")
				|| value.contains("WATER LEVEL")) {
			return "WATER";
		}

		if (value.contains("TEMPERURE")
				|| value.contains("TEMPERATURE")
				|| value.contains("HUMITY")
				|| value.contains("HUMIDITY")) {
			return "ENVIRONMENT";
		}

		if (value.contains("PIPELINE PRESSURE")) {
			return "PRESSURE";
		}

		return "OTHER";
	}
}
