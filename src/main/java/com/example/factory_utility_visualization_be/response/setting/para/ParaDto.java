package com.example.factory_utility_visualization_be.response.setting.para;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParaDto {
	private Long id;
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