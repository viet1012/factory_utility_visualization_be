package com.example.factory_utility_visualization_be.dto.overview.solar.detail;


import java.math.BigDecimal;

/**
 * kwh = null: giờ hiện tại/tương lai của hôm nay chưa có dữ liệu, khác với 0 = giờ đã kết thúc nhưng không có dữ liệu.
 */
public record SolarHourKwhDto(

		int hour,

		BigDecimal kwh

) {
}
