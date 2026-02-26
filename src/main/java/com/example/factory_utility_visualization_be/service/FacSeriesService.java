package com.example.factory_utility_visualization_be.service;

import com.example.factory_utility_visualization_be.dto.Bucket;
import com.example.factory_utility_visualization_be.dto.RangePreset;
import com.example.factory_utility_visualization_be.dto.mapper.FacSeriesRow;
import com.example.factory_utility_visualization_be.repository.FacSeriesRepo;
import com.example.factory_utility_visualization_be.response.UtilityTreeSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FacSeriesService {

	private final FacSeriesRepo repo;

	public UtilityTreeSeriesResponse getByFac(
			String fac,
			RangePreset range,
			Integer year,
			Integer month,
			String boxDeviceId,
			String plcAddress
	) {

		ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
		LocalDate today = LocalDate.now(zone);

		LocalDate fromDate;
		LocalDate toDateExclusive;

		switch (range) {
			case TODAY -> {
				fromDate = today;
				toDateExclusive = today.plusDays(1);
			}
			case YESTERDAY -> {
				fromDate = today.minusDays(1);
				toDateExclusive = today;
			}
			case LAST_7_DAYS -> {
				fromDate = today.minusDays(6);
				toDateExclusive = today.plusDays(1);
			}
			case THIS_MONTH -> {
				// ✅ nếu không truyền year/month thì fallback tháng hiện tại
				int y = (year != null) ? year : today.getYear();
				int m = (month != null) ? month : today.getMonthValue();

				LocalDate firstDay = LocalDate.of(y, m, 1);
				fromDate = firstDay;
				toDateExclusive = firstDay.plusMonths(1); // ✅ full month (exclusive)
			}
			default -> {
				fromDate = today;
				toDateExclusive = today.plusDays(1);
			}
		}

		LocalDateTime from = fromDate.atStartOfDay();
		LocalDateTime to = toDateExclusive.atStartOfDay();

		Bucket bucket = switch (range) {
			case TODAY, YESTERDAY -> Bucket.HOUR;
			case LAST_7_DAYS, THIS_MONTH -> Bucket.DAY;
		};

		List<FacSeriesRow> rows = (bucket == Bucket.HOUR)
				? repo.getSeriesHourByFac(fac, from, to, boxDeviceId, plcAddress)
				: repo.getSeriesDayByFac(fac, from, to, boxDeviceId, plcAddress);

		// cate -> boxDeviceId -> plcAddress -> rows(points)
		Map<String, Map<String, Map<String, List<FacSeriesRow>>>> tree =
				rows.stream().collect(Collectors.groupingBy(
						FacSeriesRow::getCate,
						LinkedHashMap::new,
						Collectors.groupingBy(
								FacSeriesRow::getBoxDeviceId,
								LinkedHashMap::new,
								Collectors.groupingBy(
										FacSeriesRow::getPlcAddress,
										LinkedHashMap::new,
										Collectors.toList()
								)
						)
				));

		List<UtilityTreeSeriesResponse.CateGroup> cates = new ArrayList<>();

		for (var cateEntry : tree.entrySet()) {

			List<UtilityTreeSeriesResponse.BoxDeviceGroup> boxDevices = new ArrayList<>();

			for (var boxEntry : cateEntry.getValue().entrySet()) {

				List<UtilityTreeSeriesResponse.Signal> signals = new ArrayList<>();

				for (var plcEntry : boxEntry.getValue().entrySet()) {

					List<FacSeriesRow> plcRows = plcEntry.getValue();
					if (plcRows == null || plcRows.isEmpty()) continue;

					FacSeriesRow first = plcRows.get(0);

					List<UtilityTreeSeriesResponse.Point> points = plcRows.stream()
							.map(r -> new UtilityTreeSeriesResponse.Point(r.getTs(), r.getValue()))
							.toList();

					signals.add(new UtilityTreeSeriesResponse.Signal(
							plcEntry.getKey(),
							first.getNameVi(),
							first.getNameEn(),
							first.getUnit(),
							first.getScadaId(),
							points
					));
				}

				boxDevices.add(new UtilityTreeSeriesResponse.BoxDeviceGroup(boxEntry.getKey(), signals));
			}

			cates.add(new UtilityTreeSeriesResponse.CateGroup(cateEntry.getKey(), boxDevices));
		}

		return new UtilityTreeSeriesResponse(
				fac,
				bucket.name(),
				from,
				to,
				cates
		);
	}
}