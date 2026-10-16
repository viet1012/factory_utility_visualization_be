package com.example.factory_utility_visualization_be.repository.runtime.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface PowerUsageHourProjection {
	String getFac();

	// 1 = SOLAR box, 0 = grid / main meter
	Integer getSolar();

	// 'FAC|BOX_ID' of a purpose cabinet (e.g. compressed air), else null
	String getPurposeKey();

	// start of the hour the consumption belongs to
	LocalDateTime getHourAt();

	BigDecimal getKwh();
}
