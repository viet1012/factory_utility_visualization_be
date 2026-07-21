package com.example.factory_utility_visualization_be.dto.latest;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestScadaDto {

	private String scadaId;

	private List<LatestBoxDto> boxes;
}