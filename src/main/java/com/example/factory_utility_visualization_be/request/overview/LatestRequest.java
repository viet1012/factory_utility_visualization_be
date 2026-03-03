package com.example.factory_utility_visualization_be.request.overview;

import lombok.Data;

import java.util.List;

@Data
public class LatestRequest {
	private List<String> facIds;
	private List<String> boxDeviceIds;
	private List<String> plcAddresses;
	private List<String> cateIds;
}