package com.example.factory_utility_visualization_be.repository.projection;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface HourPointView {
	LocalDateTime getTs();

	BigDecimal getValue();

	String getBoxDeviceId();
	String getPlcAddress();

	String getCateId();
	String getNameEn();
	String getNameVi();

	String getFac();
	String getCate();
}