package com.example.factory_utility_visualization_be.dto;


import lombok.Data;

@Data
public class OverlayPosDto {

	private String facId;
	private String boxDeviceId;
	private String plcAddress;
	private Double x;
	private Double y;
}
