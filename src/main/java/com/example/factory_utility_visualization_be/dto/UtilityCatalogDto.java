package com.example.factory_utility_visualization_be.dto;
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
