package com.example.factory_utility_visualization_be.dto.overview.daily;


import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyElectricityStackDto(

		LocalDate date,

		BigDecimal gridKwh,

		BigDecimal solarKwh,

		BigDecimal totalKwh,

		// EVN grid when F2_Utility_Evn_Daily has (day, scope), else = gridKwh
		// (Fac_A / Fac_B: always measured); never calibrated
		BigDecimal officialGridKwh,

		// officialGridKwh + solarKwh (total of the official stack: Grid + Solar)
		BigDecimal officialTotalKwh,

		// EVN | MEASURED
		String source

) {
}
