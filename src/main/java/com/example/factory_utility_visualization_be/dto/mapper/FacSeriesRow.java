package com.example.factory_utility_visualization_be.dto.mapper;

import java.time.LocalDateTime;

public interface FacSeriesRow {
	String getFac();
	String getCate();
	String getScadaId();

	String getBoxDeviceId();
	String getPlcAddress();

	String getNameVi();
	String getNameEn();
	String getUnit();

	LocalDateTime getTs();   // ✅ dùng chung hour/day
	Double getValue();
}