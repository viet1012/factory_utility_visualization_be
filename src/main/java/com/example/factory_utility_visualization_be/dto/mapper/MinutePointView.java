package com.example.factory_utility_visualization_be.dto.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
public interface MinutePointView {
	LocalDateTime getTs();
	BigDecimal getValue();
	String getBoxDeviceId();
	String getPlcAddress();
	String getCateId();

	String getNameEn();
	String getNameVi();

	String getUnit();   // ✅ NEW

	String getFac();
	String getCate();
}