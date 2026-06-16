package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtilityAbnormalSignalProjection {

	String getFac();

	String getScadaId();

	String getCate();

	String getSignalName();

	String getBoxDeviceId();

	String getPlcAddress();

	LocalDateTime getRecordedAt();

	BigDecimal getCurrentValue();

	BigDecimal getPrevValue();

	BigDecimal getJumpSize();

	String getStatus();

	String getDescription();
}