package com.example.factory_utility_visualization_be.service.overview;

import com.example.factory_utility_visualization_be.dto.mapper.LatestRecordView;
import com.example.factory_utility_visualization_be.repository.overview.UtilityHistoryRepository;
import com.example.factory_utility_visualization_be.response.overview.FacTimeSeriesTreeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilityLatestService {
	@Autowired
	private UtilityHistoryRepository historyRepo;

	private static List<String> norm(List<String> xs) {
		if (xs == null) return List.of();
		return xs.stream()
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.distinct()
				.toList();
	}

	private static List<String> safe(List<String> xs, String dummy) {
		return xs.isEmpty() ? List.of(dummy) : xs;
	}

	public FacTimeSeriesTreeResponse getLatestTree(
			List<String> facIds,
			List<String> boxDeviceIds,
			List<String> plcAddresses,
			List<String> cateIds
	) {

		facIds = norm(facIds);
		boxDeviceIds = norm(boxDeviceIds);
		plcAddresses = norm(plcAddresses);
		cateIds = norm(cateIds);

		int useFacIds = facIds.isEmpty() ? 0 : 1;
		int useBoxIds = boxDeviceIds.isEmpty() ? 0 : 1;
		int usePlc = plcAddresses.isEmpty() ? 0 : 1;
		int useCateIds = cateIds.isEmpty() ? 0 : 1;

		var rows = historyRepo.latestPerKeyMulti(
				useFacIds, safe(facIds, "__NO_FAC__"),
				useBoxIds, safe(boxDeviceIds, "__NO_BOX__"),
				usePlc, safe(plcAddresses, "__NO_PLC__"),
				useCateIds, safe(cateIds, "__NO_CATE__")
		);

		if (rows.isEmpty()) {
			return new FacTimeSeriesTreeResponse(
					null, "LATEST", null, null, List.of()
			);
		}

		String fac = rows.get(0).getFac();

		// GROUP cate → box → signal
		Map<String, Map<String, List<LatestRecordView>>> tree =
				rows.stream().collect(Collectors.groupingBy(
						LatestRecordView::getCate,
						Collectors.groupingBy(LatestRecordView::getBoxDeviceId)
				));

		List<FacTimeSeriesTreeResponse.CateGroup> cateGroups = tree.entrySet().stream()
				.map(cateEntry -> {

					List<FacTimeSeriesTreeResponse.BoxDeviceGroup> boxGroups =
							cateEntry.getValue().entrySet().stream()
									.map(boxEntry -> {

										List<FacTimeSeriesTreeResponse.Signal> signals =
												boxEntry.getValue().stream()
														.map(r -> new FacTimeSeriesTreeResponse.Signal(
																r.getPlcAddress(),
																null,                // nameVi (nếu cần add query)
																r.getNameEn(),
																r.getUnit(),
																r.getScadaId(),
																List.of(
																		new FacTimeSeriesTreeResponse.Point(
																				r.getRecordedAt(),
																				r.getValue() == null ? null :
																						r.getValue().doubleValue()
																		)
																)
														))
														.toList();

										return new FacTimeSeriesTreeResponse.BoxDeviceGroup(
												boxEntry.getKey(),
												signals
										);
									})
									.toList();

					return new FacTimeSeriesTreeResponse.CateGroup(
							cateEntry.getKey(),
							boxGroups
					);

				}).toList();

		return new FacTimeSeriesTreeResponse(
				fac,
				"LATEST",
				null,
				null,
				cateGroups
		);
	}
}
