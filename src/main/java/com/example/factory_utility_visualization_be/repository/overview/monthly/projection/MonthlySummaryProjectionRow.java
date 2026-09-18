package com.example.factory_utility_visualization_be.repository.overview.monthly.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MonthlySummaryProjectionRow(
		String name,
		String cate,
		String unit,
		String month,

		BigDecimal minValue,
		BigDecimal maxValue,
		BigDecimal prevMinValue,
		BigDecimal prevMaxValue,

		BigDecimal value,
		BigDecimal avgValue,

		BigDecimal vndCost,
		BigDecimal usdCost,

		BigDecimal prevValue,
		BigDecimal prevAvgValue,

		BigDecimal prevVndCost,
		BigDecimal prevUsdCost,

		BigDecimal deltaValue,
		BigDecimal deltaPercent,

		LocalDateTime pickAt,

		BigDecimal solarValue,
		BigDecimal prevSolarValue,

		BigDecimal totalEnergyValue,
		BigDecimal prevTotalEnergyValue,

		BigDecimal solarSharePercent
) implements MonthlySummaryProjection {

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getCate() {
		return cate;
	}

	@Override
	public String getUnit() {
		return unit;
	}

	@Override
	public String getMonth() {
		return month;
	}

	@Override
	public BigDecimal getMinValue() {
		return minValue;
	}

	@Override
	public BigDecimal getMaxValue() {
		return maxValue;
	}

	@Override
	public BigDecimal getPrevMinValue() {
		return prevMinValue;
	}

	@Override
	public BigDecimal getPrevMaxValue() {
		return prevMaxValue;
	}

	@Override
	public BigDecimal getValue() {
		return value;
	}

	@Override
	public BigDecimal getAvgValue() {
		return avgValue;
	}

	@Override
	public BigDecimal getVndCost() {
		return vndCost;
	}

	@Override
	public BigDecimal getUsdCost() {
		return usdCost;
	}

	@Override
	public BigDecimal getPrevValue() {
		return prevValue;
	}

	@Override
	public BigDecimal getPrevAvgValue() {
		return prevAvgValue;
	}

	@Override
	public BigDecimal getPrevVndCost() {
		return prevVndCost;
	}

	@Override
	public BigDecimal getPrevUsdCost() {
		return prevUsdCost;
	}

	@Override
	public BigDecimal getDeltaValue() {
		return deltaValue;
	}

	@Override
	public BigDecimal getDeltaPercent() {
		return deltaPercent;
	}

	@Override
	public LocalDateTime getPickAt() {
		return pickAt;
	}

	@Override
	public BigDecimal getSolarValue() {
		return solarValue;
	}

	@Override
	public BigDecimal getPrevSolarValue() {
		return prevSolarValue;
	}

	@Override
	public BigDecimal getTotalEnergyValue() {
		return totalEnergyValue;
	}

	@Override
	public BigDecimal getPrevTotalEnergyValue() {
		return prevTotalEnergyValue;
	}

	@Override
	public BigDecimal getSolarSharePercent() {
		return solarSharePercent;
	}
}