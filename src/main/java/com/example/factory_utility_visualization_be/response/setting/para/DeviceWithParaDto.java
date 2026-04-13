package com.example.factory_utility_visualization_be.response.setting.para;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceWithParaDto {
	private Long channelId;
	private String cate;
	private String boxDeviceId;
	private List<ParaDto> paras;
}
