package com.example.factory_utility_visualization_be.response.setting.para;


import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoxWithParaDto {
	private String boxId;
	private List<DeviceWithParaDto> devices;
}