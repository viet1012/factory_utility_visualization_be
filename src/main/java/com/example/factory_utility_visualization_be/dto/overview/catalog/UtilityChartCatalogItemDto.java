package com.example.factory_utility_visualization_be.dto.overview.catalog;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilityChartCatalogItemDto {

	private String fac;
	private String scadaId;
	private String cate;

	private String boxId;
	private String boxDeviceId;

	private Long paraId;
	private String plcAddress;

	private String valueType;
	private String unit;
	private String cateId;

	private String nameVi;
	private String nameEn;

	private Integer isImportant;
	private Integer isAlert;

	private Double minAlert;
	private Double maxAlert;
}