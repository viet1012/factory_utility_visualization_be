package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;


import java.time.LocalDateTime;

public interface UtilitySignalHealthMatrixProjection {

	String getFac();

	String getScadaId();

	String getCate();

	String getSignalName();

	String getUnit();

	String getBoxDeviceId();

	String getPlcAddress();

	LocalDateTime getRecordedAt();

	Double getCurrentValue();

	Double getPrevValue();

	Double getJumpSize();

	String getStatus();

	String getDescription();
}