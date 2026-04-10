package com.example.factory_utility_visualization_be.service.setting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoxDto {
	private String boxId;
	private List<DeviceDto> devices;
}