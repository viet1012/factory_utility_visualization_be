package com.example.factory_utility_visualization_be.request.setting;


import lombok.Data;

@Data
public class UtilityParaRequest {

	private String boxDeviceId;
	private String plcAddress;
	private String valueType;
	private String unit;
	private String cateId;
	private String nameVi;
	private String nameEn;
	private Integer isImportant;
	private Integer isAlert;
	private Long minAlert;
	private Long maxAlert;
}