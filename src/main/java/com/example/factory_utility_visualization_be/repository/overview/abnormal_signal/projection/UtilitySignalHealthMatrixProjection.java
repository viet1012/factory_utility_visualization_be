package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection;


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