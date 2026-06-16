package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;



import java.util.List;

public record CategoryDto(

		String cate,

		List<ScadaDto> scadas

) {
}