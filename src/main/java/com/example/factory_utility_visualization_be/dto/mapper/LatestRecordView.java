package com.example.factory_utility_visualization_be.dto.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface LatestRecordView {
	String getBoxDeviceId();
	String getPlcAddress();
	BigDecimal getValue();
	LocalDateTime getRecordedAt();

	String getCateId();
	String getScadaId();
	String getFac();
	String getCate();
	String getBoxId();
}
