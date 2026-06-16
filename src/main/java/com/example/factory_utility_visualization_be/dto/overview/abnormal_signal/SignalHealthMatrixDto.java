package com.example.factory_utility_visualization_be.dto.overview.abnormal_signal;


import java.util.List;

public record SignalHealthMatrixDto(
		String fac,
		String cate,
		String scadaId,
		String boxDeviceId,
		int totalRegisters,
		int ngRegisters,
		String status,
		List<SignalHealthMatrixItemDto> signals
) {
}