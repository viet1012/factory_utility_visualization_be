package com.example.factory_utility_visualization_be.dto;

import com.example.factory_utility_visualization_be.dto.runtime.ChannelDto;
import com.example.factory_utility_visualization_be.dto.runtime.LatestRecordDto;
import com.example.factory_utility_visualization_be.dto.runtime.ParamDto;
import com.example.factory_utility_visualization_be.dto.runtime.ScadaDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UtilityCatalogDto {
	private List<ScadaDto> scadas = new ArrayList<>();
	private List<ChannelDto> channels = new ArrayList<>();
	private List<ParamDto> params = new ArrayList<>();
	private List<LatestRecordDto> latest = new ArrayList<>();
}
