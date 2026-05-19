package com.example.factory_utility_visualization_be.dto.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//package com.example.factory_utility_visualization_be.dto.mapper;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//
//public interface LatestRecordView {
//
//	String getBoxDeviceId();
//	String getPlcAddress();
//	BigDecimal getValue();
//	LocalDateTime getRecordedAt();
//
//	String getCateId();
//	String getNameEn();   // ✅ ADD
//	String getUnit();     // optional
//
//	String getScadaId();
//	String getFac();
//	String getCate();
//	String getBoxId();
//}

public interface LatestRecordView {
	String getBoxDeviceId();
	String getPlcAddress();
	BigDecimal getValue();
	LocalDateTime getRecordedAt();

	String getCateId();
	String getNameEn();
	String getUnit();

	String getScadaId();
	String getFac();
	String getCate();
	String getBoxId();

	Double getMinVol();
	Double getMaxVol();
	Double getMinVolStd();
	Double getMaxVolStd();
	String getAlarm();
}