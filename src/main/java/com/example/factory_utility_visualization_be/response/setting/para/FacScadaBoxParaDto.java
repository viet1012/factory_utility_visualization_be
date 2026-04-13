package com.example.factory_utility_visualization_be.response.setting.para;


import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacScadaBoxParaDto {
	private String fac;
	private String scadaId;
	private List<BoxWithParaDto> boxes;
}