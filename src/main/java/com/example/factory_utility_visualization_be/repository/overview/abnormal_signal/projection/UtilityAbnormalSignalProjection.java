package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection;


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