package com.example.factory_utility_visualization_be.dto.overview.electric;

import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.BillPeriod;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.DayPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MeterIssue;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MissingMeter;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.MonthPoint;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Official;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Scope;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.UnitPrices;
import com.example.factory_utility_visualization_be.dto.overview.electric.ElectricConsumptionResponseDto.Values;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/utility/electric-consumption?view=measured|official: projections
 * of the full response (ElectricConsumptionResponseDto, no view), same values
 * and meaning, only the fields of one layer.
 */
public final class ElectricConsumptionViews {

	private ElectricConsumptionViews() {}

	// ---------------------------------------------------------------------
	// view=measured: meter data only (no official / calibrated / evn)
	// ---------------------------------------------------------------------

	public record MeasuredResponse(
			String month,
			BillPeriod billPeriod,
			String dataUntil,
			boolean tariffAvailable,
			BigDecimal minCoveragePct,
			int hourOffset,
			String historyStartDate,
			List<String> excludedMeters,
			UnitPrices unitPrices,
			List<MeasuredScope> scopes
	) {
		public static MeasuredResponse of(ElectricConsumptionResponseDto full) {
			return new MeasuredResponse(
					full.month(),
					full.billPeriod(),
					full.dataUntil(),
					full.tariffAvailable(),
					full.minCoveragePct(),
					full.hourOffset(),
					full.historyStartDate(),
					full.excludedMeters(),
					full.unitPrices(),
					full.scopes().stream().map(MeasuredScope::of).toList());
		}
	}

	public record MeasuredScope(
			String code,
			String name,
			List<MeasuredMonth> monthly,
			List<MeasuredDay> daily,
			int meters,
			int elapsedDays,
			int daysWithData,
			int completeDays,
			Values dailyTotalMwh,
			Values dailyTotalKusd,
			Values dailyAverageMwh,
			Values dailyAverageKusd,
			List<MeterIssue> meterIssues,
			boolean priceMissing,
			BigDecimal estimatedMissingMwh
	) {
		static MeasuredScope of(Scope s) {
			return new MeasuredScope(
					s.code(),
					s.name(),
					s.monthly().stream().map(MeasuredMonth::of).toList(),
					s.daily().stream().map(MeasuredDay::of).toList(),
					s.meters(),
					s.elapsedDays(),
					s.daysWithData(),
					s.completeDays(),
					s.dailyTotalMwh(),
					s.dailyTotalKusd(),
					s.dailyAverageMwh(),
					s.dailyAverageKusd(),
					s.meterIssues(),
					s.priceMissing(),
					s.estimatedMissingMwh());
		}
	}

	public record MeasuredMonth(
			String month,
			String label,
			String from,
			String to,
			int days,
			int meters,
			int daysWithData,
			int completeDays,
			boolean partial,
			BigDecimal excludedMwh,
			Values perDayMwh,
			Values perDayKusd,
			boolean priceMissing
	) {
		static MeasuredMonth of(MonthPoint m) {
			return new MeasuredMonth(m.month(), m.label(), m.from(), m.to(), m.days(), m.meters(),
					m.daysWithData(), m.completeDays(), m.partial(), m.excludedMwh(), m.perDayMwh(),
					m.perDayKusd(), m.priceMissing());
		}
	}

	public record MeasuredDay(
			String date,
			String weekday,
			int hoursWithData,
			int meters,
			BigDecimal coveragePct,
			boolean partial,
			BigDecimal excludedMwh,
			Values mwh,
			Values kusd,
			int reportingMeters,
			List<MissingMeter> missingMeters
	) {
		static MeasuredDay of(DayPoint d) {
			return new MeasuredDay(d.date(), d.weekday(), d.hoursWithData(), d.meters(), d.coveragePct(),
					d.partial(), d.excludedMwh(), d.mwh(), d.kusd(), d.reportingMeters(), d.missingMeters());
		}
	}

	// ---------------------------------------------------------------------
	// view=official: EVN when imported, else measured (per day / scope / month)
	// ---------------------------------------------------------------------

	public record OfficialResponse(
			String month,
			BillPeriod billPeriod,
			String dataUntil,
			boolean tariffAvailable,
			int hourOffset,
			UnitPrices unitPrices,
			List<OfficialScope> scopes
	) {
		public static OfficialResponse of(ElectricConsumptionResponseDto full) {
			return new OfficialResponse(
					full.month(),
					full.billPeriod(),
					full.dataUntil(),
					full.tariffAvailable(),
					full.hourOffset(),
					full.unitPrices(),
					full.scopes().stream().map(OfficialScope::of).toList());
		}
	}

	public record OfficialScope(
			String code,
			String name,
			List<OfficialMonth> monthly,
			List<OfficialDay> daily,
			Values officialTotalMwh,
			Values officialTotalKusd,
			Values officialAverageMwh,
			Values officialAverageKusd,
			int evnDays,
			int measuredDays
	) {
		static OfficialScope of(Scope s) {
			return new OfficialScope(
					s.code(),
					s.name(),
					s.monthly().stream().map(OfficialMonth::of).toList(),
					s.daily().stream().map(OfficialDay::of).toList(),
					s.officialTotalMwh(),
					s.officialTotalKusd(),
					s.officialAverageMwh(),
					s.officialAverageKusd(),
					s.evnDays(),
					s.measuredDays());
		}
	}

	// average per day of the official values (MonthPoint.official), null = no day
	public record OfficialMonth(
			String month,
			String label,
			String from,
			String to,
			int days,
			Values mwh,
			Values kusd,
			int evnDays,
			int measuredDays
	) {
		static OfficialMonth of(MonthPoint m) {
			return new OfficialMonth(m.month(), m.label(), m.from(), m.to(), m.days(),
					m.official() == null ? null : m.official().mwh(),
					m.official() == null ? null : m.official().kusd(),
					m.evnDays(), m.measuredDays());
		}
	}

	public record OfficialDay(
			String date,
			String weekday,
			// mwh / kusd / source (EVN | MEASURED), null = neither EVN nor measured data
			Official official
	) {
		static OfficialDay of(DayPoint d) {
			return new OfficialDay(d.date(), d.weekday(), d.official());
		}
	}
}
