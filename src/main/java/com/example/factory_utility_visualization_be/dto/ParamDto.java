package com.example.factory_utility_visualization_be.dto;


import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParamDto {
	private Long id;
	private String boxDeviceId;
	private String plcAddress;
	private String valueType;
	private String unit;
	private String category;
	private String nameVi;
	private String nameEn;
	private Integer isImportant;
	private Integer isAlert;
	private String cateName;  // ✅ new
	private BigDecimal pickHour;  // ✅ new
	// enrich từ join (optional)
	private String cateId;

	private String scadaId;
	private String fac;
	private String cate;
	private String boxId;
}
