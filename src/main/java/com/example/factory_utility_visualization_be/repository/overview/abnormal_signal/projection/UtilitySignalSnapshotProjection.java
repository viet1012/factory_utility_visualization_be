package com.example.factory_utility_visualization_be.repository.overview.abnormal_signal.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface UtilitySignalSnapshotProjection {
	String getFac();
	String getScadaId();
	String getCate();
	Long getParaId();
	String getParameterCode();
	String getSignalName();
	String getUnit();
	String getBoxDeviceId();
	String getPlcAddress();
	LocalDateTime getRecordedAt();
	BigDecimal getCurrentValue();
	BigDecimal getPrevValue();
}
