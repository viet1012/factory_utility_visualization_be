package com.example.factory_utility_visualization_be.service.setting;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceDto {
	private Long channelId;
	private String cate;
	private String boxDeviceId;
}