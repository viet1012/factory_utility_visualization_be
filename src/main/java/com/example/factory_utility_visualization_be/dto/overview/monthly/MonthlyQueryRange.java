package com.example.factory_utility_visualization_be.dto.overview.monthly;

import java.time.LocalDateTime;

public record MonthlyQueryRange(
		LocalDateTime from,
		LocalDateTime currentTo,
		LocalDateTime prevFrom,
		LocalDateTime prevTo
) {
}