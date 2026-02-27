package com.example.factory_utility_visualization_be.response.overview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FacTimeSeriesTreeResponse {
	private String fac;
	private String bucket;          // "HOUR" | "DAY"
	private LocalDateTime from;
	private LocalDateTime to;
	private List<CateGroup> cates;

	@Data @AllArgsConstructor @NoArgsConstructor
	public static class CateGroup {
		private String cate;
		private List<BoxDeviceGroup> boxDevices;
	}

	@Data @AllArgsConstructor @NoArgsConstructor
	public static class BoxDeviceGroup {
		private String boxDeviceId;
		private List<Signal> signals;
	}

	@Data @AllArgsConstructor @NoArgsConstructor
	public static class Signal {
		private String plcAddress;
		private String nameVi;
		private String nameEn;
		private String unit;
		private String scadaId;
		private List<Point> points;
	}

	@Data @AllArgsConstructor @NoArgsConstructor
	public static class Point {
		private LocalDateTime ts;
		private Double value;
	}
}