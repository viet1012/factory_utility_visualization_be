package com.example.factory_utility_visualization_be.service.overview;

import com.example.factory_utility_visualization_be.dto.Bucket;
import com.example.factory_utility_visualization_be.dto.RangePreset;
import com.example.factory_utility_visualization_be.dto.overview.FacTimeSeriesRow;
import com.example.factory_utility_visualization_be.repository.overview.FacTimeSeriesRepository;
import com.example.factory_utility_visualization_be.response.overview.FacTimeSeriesTreeResponse;
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
public class FacTimeSeriesService {

	private final FacTimeSeriesRepository repo;

	public FacTimeSeriesTreeResponse getFacTimeSeriesTree(
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

		List<FacTimeSeriesRow> rows = (bucket == Bucket.HOUR)
				? repo.getSeriesHourByFac(fac, from, to, boxDeviceId, plcAddress)
				: repo.getSeriesDayByFac(fac, from, to, boxDeviceId, plcAddress);

		// cate -> boxDeviceId -> plcAddress -> rows(points)
		Map<String, Map<String, Map<String, List<FacTimeSeriesRow>>>> tree =
				rows.stream().collect(Collectors.groupingBy(
						FacTimeSeriesRow::getCate,
						LinkedHashMap::new,
						Collectors.groupingBy(
								FacTimeSeriesRow::getBoxDeviceId,
								LinkedHashMap::new,
								Collectors.groupingBy(
										FacTimeSeriesRow::getPlcAddress,
										LinkedHashMap::new,
										Collectors.toList()
								)
						)
				));

		List<FacTimeSeriesTreeResponse.CateGroup> cates = new ArrayList<>();

		for (var cateEntry : tree.entrySet()) {

			List<FacTimeSeriesTreeResponse.BoxDeviceGroup> boxDevices = new ArrayList<>();

			for (var boxEntry : cateEntry.getValue().entrySet()) {

				List<FacTimeSeriesTreeResponse.Signal> signals = new ArrayList<>();

				for (var plcEntry : boxEntry.getValue().entrySet()) {

					List<FacTimeSeriesRow> plcRows = plcEntry.getValue();
					if (plcRows == null || plcRows.isEmpty()) continue;

					FacTimeSeriesRow first = plcRows.get(0);

					List<FacTimeSeriesTreeResponse.Point> points = plcRows.stream()
							.map(r -> new FacTimeSeriesTreeResponse.Point(r.getTs(), r.getValue()))
							.toList();

					signals.add(new FacTimeSeriesTreeResponse.Signal(
							plcEntry.getKey(),
							first.getNameVi(),
							first.getNameEn(),
							first.getUnit(),
							first.getScadaId(),
							points
					));
				}

				boxDevices.add(new FacTimeSeriesTreeResponse.BoxDeviceGroup(boxEntry.getKey(), signals));
			}

			cates.add(new FacTimeSeriesTreeResponse.CateGroup(cateEntry.getKey(), boxDevices));
		}

		return new FacTimeSeriesTreeResponse(
				fac,
				bucket.name(),
				from,
				to,
				cates
		);
	}
}