package com.example.factory_utility_visualization_be.dto.overview.catalog;


public interface UtilityChartCatalogProjection {

	String getFac();

	String getScadaId();

	String getCate();

	String getBoxId();

	String getBoxDeviceId();

	Long getParaId();

	String getPlcAddress();

	String getValueType();

	String getUnit();

	String getCateId();

	String getNameVi();

	String getNameEn();

	Integer getIsImportant();

	Integer getIsAlert();

	Double getMinAlert();

	Double getMaxAlert();
}