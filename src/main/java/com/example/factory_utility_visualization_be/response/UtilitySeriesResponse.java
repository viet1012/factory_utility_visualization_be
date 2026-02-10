package com.example.factory_utility_visualization_be.response;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilitySeriesResponse {

	private List<SeriesItem> series;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class SeriesItem {
		private String boxDeviceId;
		private String plcAddress;
		private List<Point> points;
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	public static class Point {
		private LocalDateTime t;
		private BigDecimal v;
	}
}
