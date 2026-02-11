package com.example.factory_utility_visualization_be.dto.mapper;

import java.time.LocalDateTime;

public interface MinutePointView {
	LocalDateTime getTs();     // thời điểm phút (rounded)
	java.math.BigDecimal getValue();     // giá trị (avg/max/last...)
	String getBoxDeviceId();
	String getPlcAddress();
	String getCateId();                  // optional enrich
}
