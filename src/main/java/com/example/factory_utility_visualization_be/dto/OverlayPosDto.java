package com.example.factory_utility_visualization_be.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OverlayPosDto {
	private String facId;
	private String boxDeviceId;
	private Double x;
	private Double y;
	private String direction;
}