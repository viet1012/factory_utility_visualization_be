package com.example.factory_utility_visualization_be.service.overview.hourly;


import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyCompareDto;
import com.example.factory_utility_visualization_be.dto.overview.hourly.HourlyTempCompareDto;
import com.example.factory_utility_visualization_be.repository.overview.hourly.UtilityHourlyRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UtilityHourlyService {

	private final UtilityHourlyRepo repo;

	public UtilityHourlyService(UtilityHourlyRepo repo) {
		this.repo = repo;
	}

	public List<HourlyCompareDto> getHourly(
			String fac,
			int hours,
			String nameEn,
			BigDecimal exchange,
			BigDecimal sepzone
	) {
		final String metric = (nameEn == null || nameEn.isBlank())
				? "Total Energy Consumption"
				: nameEn.trim();

		final BigDecimal safeExchange =
				(exchange == null || BigDecimal.ZERO.compareTo(exchange) == 0)
						? new BigDecimal("26005")
						: exchange;

		final BigDecimal safeSepzone =
				(sepzone == null)
						? new BigDecimal("1.075")
						: sepzone;

		return repo.hourlyCompareDto(fac, hours, metric, safeExchange, safeSepzone);
	}

	public List<HourlyTempCompareDto> getCoolingTankHourly(
			String facId,
			Integer hours
	) {
		String fac = facId == null || facId.isBlank() ? "KVH" : facId.trim();
		int h = hours == null || hours <= 0 ? 24 : Math.min(hours, 24 * 7);

		return repo.findCoolingTankHourlyCompareDto(fac, h);
	}
}