package com.example.factory_utility_visualization_be.dto.overview.catalog;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilityChartCatalogResponse {

	private String facId;
	private String cate;
	private String scadaId;

	private int totalScadas;
	private int totalBoxes;
	private int totalDevices;
	private int totalParams;

	private List<UtilityChartCatalogItemDto> items;
}