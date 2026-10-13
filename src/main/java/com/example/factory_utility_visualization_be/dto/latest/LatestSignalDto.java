package com.example.factory_utility_visualization_be.dto.latest;


import com.example.factory_utility_visualization_be.dto.runtime.history.SignalKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LatestSignalDto {

	private String plcAddress;

	private String cateId;

	private String nameEn;

	private BigDecimal value;

	private String unit;

	private LocalDateTime recordedAt;

	// ANALOG | STATUS | COUNTER, same resolver as /series/history
	private SignalKind kind;
}
