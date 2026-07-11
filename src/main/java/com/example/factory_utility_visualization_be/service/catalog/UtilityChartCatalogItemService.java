package com.example.factory_utility_visualization_be.service.catalog;

import com.example.factory_utility_visualization_be.dto.overview.catalog.UtilityChartCatalogItemDto;
import com.example.factory_utility_visualization_be.dto.overview.catalog.UtilityChartCatalogProjection;
import com.example.factory_utility_visualization_be.dto.overview.catalog.UtilityChartCatalogResponse;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

import static com.example.factory_utility_visualization_be.service.UtilityQueryService.blankToNull;

@Service
@RequiredArgsConstructor
public class UtilityChartCatalogItemService {

	@Autowired
	private  F2UtilityParaRepo paraRepo;

	@Transactional()
	public UtilityChartCatalogResponse getChartCatalog(
			String facId,
			String cate,
			String scadaId,
			String boxId,
			String boxDeviceId,
			Integer importantOnly
	) {
		final String facFilter = blankToNull(facId);
		final String cateFilter = blankToNull(cate);
		final String scadaFilter = blankToNull(scadaId);
		final String boxFilter = blankToNull(boxId);
		final String deviceFilter = blankToNull(boxDeviceId);

		final int important = Objects.equals(importantOnly, 1) ? 1 : 0;

		List<UtilityChartCatalogProjection> rows =
				paraRepo.findChartCatalog(
						facFilter,
						cateFilter,
						scadaFilter,
						boxFilter,
						deviceFilter,
						important
				);

		List<UtilityChartCatalogItemDto> items = rows.stream()
				.filter(row ->
						row.getBoxDeviceId() != null
								&& !row.getBoxDeviceId().isBlank()
								&& row.getPlcAddress() != null
								&& !row.getPlcAddress().isBlank()
				)
				.map(this::toChartCatalogItem)
				.toList();

		int totalScadas = (int) items.stream()
				.map(UtilityChartCatalogItemDto::getScadaId)
				.filter(Objects::nonNull)
				.distinct()
				.count();

		int totalBoxes = (int) items.stream()
				.map(item ->
						safe(item.getScadaId())
								+ "|"
								+ safe(item.getBoxId())
				)
				.distinct()
				.count();

		int totalDevices = (int) items.stream()
				.map(item ->
						safe(item.getScadaId())
								+ "|"
								+ safe(item.getBoxId())
								+ "|"
								+ safe(item.getBoxDeviceId())
				)
				.distinct()
				.count();

		return UtilityChartCatalogResponse.builder()
				.facId(facFilter)
				.cate(cateFilter)
				.scadaId(scadaFilter)
				.totalScadas(totalScadas)
				.totalBoxes(totalBoxes)
				.totalDevices(totalDevices)
				.totalParams(items.size())
				.items(items)
				.build();
	}

	private UtilityChartCatalogItemDto toChartCatalogItem(
			UtilityChartCatalogProjection row
	) {
		return UtilityChartCatalogItemDto.builder()
				.fac(trimToNull(row.getFac()))
				.scadaId(trimToNull(row.getScadaId()))
				.cate(trimToNull(row.getCate()))
				.boxId(trimToNull(row.getBoxId()))
				.boxDeviceId(trimToNull(row.getBoxDeviceId()))
				.paraId(row.getParaId())
				.plcAddress(trimToNull(row.getPlcAddress()))
				.valueType(trimToNull(row.getValueType()))
				.unit(trimToNull(row.getUnit()))
				.cateId(trimToNull(row.getCateId()))
				.nameVi(trimToNull(row.getNameVi()))
				.nameEn(trimToNull(row.getNameEn()))
				.isImportant(row.getIsImportant())
				.isAlert(row.getIsAlert())
				.minAlert(row.getMinAlert())
				.maxAlert(row.getMaxAlert())
				.build();
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
