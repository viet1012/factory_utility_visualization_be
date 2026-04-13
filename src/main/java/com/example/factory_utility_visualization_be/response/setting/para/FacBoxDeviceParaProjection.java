package com.example.factory_utility_visualization_be.response.setting.para;

public interface FacBoxDeviceParaProjection {
	String getFac();
	String getScadaId();
	Long getChannelId();
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
	Long getMinAlert();
	Long getMaxAlert();
}