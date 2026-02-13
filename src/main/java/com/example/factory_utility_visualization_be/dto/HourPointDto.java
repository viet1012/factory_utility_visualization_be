package com.example.factory_utility_visualization_be.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HourPointDto {
	private LocalDateTime ts;
	private BigDecimal value;

	private String boxDeviceId;
	private String plcAddress;

	private String cateId;
	private String nameEn;
	private String nameVi;

	private String fac;
	private String cate;
}
