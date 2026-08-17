package com.example.factory_utility_visualization_be.service.overview.period;

import com.example.factory_utility_visualization_be.dto.overview.period.UtilityPeriodBoxDto;
import com.example.factory_utility_visualization_be.dto.overview.period.UtilityPeriodBoxTrendDto;
import com.example.factory_utility_visualization_be.dto.overview.period.UtilityPeriodDashboardDto;
import com.example.factory_utility_visualization_be.dto.overview.period.UtilityPeriodTrendDto;
import com.example.factory_utility_visualization_be.dto.overview.period.projection.UtilityPeriodBoxDailyProjection;
import com.example.factory_utility_visualization_be.dto.overview.period.projection.UtilityPeriodBoxProjection;
import com.example.factory_utility_visualization_be.dto.overview.period.projection.UtilityPeriodTrendProjection;
import com.example.factory_utility_visualization_be.repository.overview.period.UtilityPeriodRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UtilityPeriodService {

	// ============================================================
	// DASHBOARD
	// ============================================================
	private static final ZoneId APP_ZONE =
			ZoneId.of("Asia/Ho_Chi_Minh");
	private final UtilityPeriodRepo repo;

	@Transactional(readOnly = true)
	public UtilityPeriodDashboardDto getDashboard(
			String facId,
			String type,
			String period,
			String date
	) {

		// ============================================================
		// 1. INPUT
		// ============================================================

		final String fac =
				normalizeFac(
						facId
				);

		final String utilityType =
				normalizeType(
						type
				);

		final String periodType =
				normalizePeriod(
						period
				);

		final LocalDate selectedDate =
				parseDate(
						date
				);

		// ============================================================
		// 2. DISPLAY RANGE
		//
		// WEEK:
		// Monday -> Sunday
		//
		// MONTH:
		// 01 -> end month
		//
		// Chỉ dùng để xác định layout/chart.
		// ============================================================

		final DateRange displayRange =
				resolveRange(
						selectedDate,
						periodType
				);

		// ============================================================
		// 3. COMPARISON RANGE
		//
		// Nếu period hiện tại:
		//
		// WEEK:
		// current:
		// Monday -> NOW
		//
		// previous:
		// Monday tuần trước -> cùng thời điểm
		//
		// MONTH:
		// current:
		// 01 tháng này -> NOW
		//
		// previous:
		// 01 tháng trước -> cùng ngày/cùng giờ
		// ============================================================

		final PeriodComparisonRange comparisonRange =
				resolvePeriodComparisonRange(
						displayRange,
						periodType
				);

		// ============================================================
		// 4. MODE
		// ============================================================

		final boolean sumMode =
				"ELECTRICITY".equals(
						utilityType
				);

		// ============================================================
		// 5. CURRENT DATA
		//
		// Chỉ query tới currentTo.
		// Không query tương lai.
		// ============================================================

//		final UtilityData current =
//				loadCurrentData(
//						utilityType,
//						fac,
//						comparisonRange.currentFrom(),
//						comparisonRange.currentTo()
//				);
		final UtilityData current =
				loadCurrentDataOptimized(
						utilityType,
						fac,
						comparisonRange.currentFrom(),
						comparisonRange.currentTo()
				);
		// ============================================================
		// 6. PREVIOUS SAME PROGRESS
		// ============================================================

		final List<UtilityPeriodTrendProjection> previousTrendRows =
				loadTrend(
						utilityType,
						fac,
						comparisonRange.previousFrom(),
						comparisonRange.previousTo()
				);

		// ============================================================
		// 7. TREND
		// ============================================================

		final List<UtilityPeriodTrendDto> trend =
				buildTrend(
						current.trendRows()
				);

		// ============================================================
		// 8. CURRENT TOTAL
		// ============================================================

		final BigDecimal total =
				aggregateTrend(
						current.trendRows(),
						sumMode
				);

		// ============================================================
		// 9. PREVIOUS SAME-PROGRESS TOTAL
		// ============================================================

		final BigDecimal previousTotal =
				aggregateTrend(
						previousTrendRows,
						sumMode
				);

		// ============================================================
		// 10. CHANGE %
		// ============================================================

		final BigDecimal changePercent =
				calculateChangePercent(
						total,
						previousTotal
				);

		// ============================================================
		// 11. BY BOX
		// ============================================================

		final List<UtilityPeriodBoxDto> byBox =
				buildByBox(
						current.boxRows(),
						total,
						sumMode
				);

		// ============================================================
		// 12. HEATMAP
		// ============================================================

		final HeatmapResult heatmap =
				buildHeatmap(
						current.boxDailyRows(),
						displayRange,
						periodType,
						sumMode
				);

		// ============================================================
		// 13. UNIT
		// ============================================================

		final String unit =
				resolveUnit(
						current.trendRows()
				);

		// ============================================================
		// 14. RESPONSE
		// ============================================================

		return new UtilityPeriodDashboardDto(

				fac,

				utilityType,

				periodType,

				displayRange.from(),

				displayRange
						.toExclusive()
						.minusDays(1),

				unit,

				LocalDateTime.now(
						APP_ZONE
				),

				// Current same progress
				total,

				// Previous same progress
				previousTotal,

				changePercent,

				trend,

				byBox,

				heatmap.columns(),

				heatmap.rows(),

				heatmap.columnTotals(),

				heatmap.grandTotal()
		);
	}

	private PeriodComparisonRange resolvePeriodComparisonRange(
			DateRange displayRange,
			String period
	) {

		final LocalDateTime now =
				LocalDateTime.now(
						APP_ZONE
				);

		final LocalDateTime displayFrom =
				displayRange
						.from()
						.atStartOfDay();

		final LocalDateTime displayTo =
				displayRange
						.toExclusive()
						.atStartOfDay();

		// ============================================================
		// PERIOD ĐANG CHỌN CÓ PHẢI PERIOD HIỆN TẠI KHÔNG?
		// ============================================================

		final boolean isCurrentPeriod =
				!now.isBefore(
						displayFrom
				)
						&&
						now.isBefore(
								displayTo
						);

		// ============================================================
		// CURRENT END
		//
		// Current period -> NOW
		// Past period    -> end period
		// ============================================================

		final LocalDateTime currentTo =
				isCurrentPeriod
						? now
						: displayTo;

		// ============================================================
		// WEEK
		// ============================================================

		if ("WEEK".equals(period)) {

			return new PeriodComparisonRange(
					displayFrom,
					currentTo,

					displayFrom.minusDays(7),
					currentTo.minusDays(7)
			);
		}
		// ============================================================
		// MONTH
		// ============================================================

		return new PeriodComparisonRange(

				displayFrom,

				currentTo,

				displayFrom.minusMonths(1),

				currentTo.minusMonths(1)
		);
	}
	// ============================================================
	// LOAD CURRENT DATA
	//
	// Dùng đúng 9 method đang có trong Repository.
	// ============================================================
// ============================================================
// SAME TIME TODAY VS YESTERDAY
//
// Ví dụ:
//
// now = 2026-08-14 09:09
//
// CURRENT:
// 2026-08-14 00:00
// -> 2026-08-14 09:09
//
// PREVIOUS:
// 2026-08-13 00:00
// -> 2026-08-13 09:09
//
// ELECTRICITY -> SUM
// WATER/AIR   -> AVG
// ============================================================


	private UtilityData loadCurrentData(
			String utilityType,
			String fac,
			LocalDateTime fromTime,
			LocalDateTime toTime
	) {

		return switch (utilityType) {

			// ====================================================
			// ELECTRICITY
			// ====================================================

			case "ELECTRICITY" -> new UtilityData(

					repo.getElectricityTrend(
							fac,
							fromTime,
							toTime
					),

					repo.getElectricityByBox(
							fac,
							fromTime,
							toTime
					),

					repo.getElectricityBoxDaily(
							fac,
							fromTime,
							toTime
					)
			);

			// ====================================================
			// WATER
			// ====================================================

			case "WATER" -> new UtilityData(

					repo.getWaterTrend(
							fac,
							fromTime,
							toTime
					),

					repo.getWaterByBox(
							fac,
							fromTime,
							toTime
					),

					repo.getWaterBoxDaily(
							fac,
							fromTime,
							toTime
					)
			);

			// ====================================================
			// AIR
			// ====================================================

			case "AIR" -> new UtilityData(

					repo.getAirTrend(
							fac,
							fromTime,
							toTime
					),

					repo.getAirByBox(
							fac,
							fromTime,
							toTime
					),

					repo.getAirBoxDaily(
							fac,
							fromTime,
							toTime
					)
			);

			default -> throw new IllegalArgumentException(
					"Unsupported utility type: "
							+ utilityType
			);
		};
	}


	private UtilityData loadCurrentDataOptimized(
			String utilityType,
			String fac,
			LocalDateTime fromTime,
			LocalDateTime toTime
	) {

		// ============================================================
		// ELECTRICITY
		//
		// BoxDaily đã đủ dữ liệu để dựng:
		//
		// - Trend
		// - ByBox
		// - Heatmap
		//
		// => chỉ scan History 1 lần cho current period.
		// ============================================================

		if ("ELECTRICITY".equals(utilityType)) {

			final List<UtilityPeriodBoxDailyProjection> boxDailyRows =
					repo.getElectricityBoxDaily(
							fac,
							fromTime,
							toTime
					);

			return buildElectricityUtilityData(
					boxDailyRows
			);
		}

		// ============================================================
		// WATER / AIR
		//
		// Không derive từ BoxDaily vì AVG(AVG(...)) có thể sai.
		// Giữ query chuyên biệt.
		// ============================================================

		return switch (utilityType) {

			case "WATER" -> new UtilityData(

					repo.getWaterTrend(
							fac,
							fromTime,
							toTime
					),

					repo.getWaterByBox(
							fac,
							fromTime,
							toTime
					),

					repo.getWaterBoxDaily(
							fac,
							fromTime,
							toTime
					)
			);

			case "AIR" -> new UtilityData(

					repo.getAirTrend(
							fac,
							fromTime,
							toTime
					),

					repo.getAirByBox(
							fac,
							fromTime,
							toTime
					),

					repo.getAirBoxDaily(
							fac,
							fromTime,
							toTime
					)
			);

			default -> throw new IllegalArgumentException(
					"Unsupported utility type: "
							+ utilityType
			);
		};
	}

	private UtilityData buildElectricityUtilityData(
			List<UtilityPeriodBoxDailyProjection> source
	) {

		if (source == null ||
				source.isEmpty()) {

			return new UtilityData(
					List.of(),
					List.of(),
					List.of()
			);
		}

		// ============================================================
		// 1. TREND
		//
		// date -> SUM tất cả device
		// ============================================================

		final Map<LocalDate, BigDecimal> dailyTotals =
				new HashMap<>();

		// ============================================================
		// 2. BY BOX
		//
		// boxId -> SUM toàn period
		// ============================================================

		final Map<String, BigDecimal> boxTotals =
				new HashMap<>();

		final Map<String, String> boxDeviceIds =
				new HashMap<>();

		for (UtilityPeriodBoxDailyProjection row : source) {

			if (row == null) {
				continue;
			}

			final LocalDate recordDate =
					row.getRecordDate();

			final String boxId =
					normalizeBoxId(
							row.getBoxId()
					);

			final BigDecimal value =
					zero(
							row.getValue()
					);

			// ==========================================================
			// TREND
			// ==========================================================

			if (recordDate != null) {

				dailyTotals.merge(
						recordDate,
						value,
						BigDecimal::add
				);
			}

			// ==========================================================
			// BY BOX
			// ==========================================================

			if (boxId != null) {

				boxTotals.merge(
						boxId,
						value,
						BigDecimal::add
				);

				if (row.getBoxDeviceId() != null) {
					boxDeviceIds.putIfAbsent(
							boxId,
							row.getBoxDeviceId()
					);
				}
			}
		}

		// ============================================================
		// TREND PROJECTION
		// ============================================================

		final List<UtilityPeriodTrendProjection> trendRows =
				dailyTotals
						.entrySet()
						.stream()

						.sorted(
								Map.Entry.comparingByKey()
						)

						.map(entry ->
								new SimpleTrendProjection(
										entry.getKey(),
										entry.getValue(),
										"kWh"
								)
						)

						.map(
								UtilityPeriodTrendProjection.class::cast
						)

						.toList();

		// ============================================================
		// BY BOX PROJECTION
		// ============================================================

		final List<UtilityPeriodBoxProjection> boxRows =
				boxTotals
						.entrySet()
						.stream()

						.sorted(
								Map.Entry
										.<String, BigDecimal>
												comparingByValue()
										.reversed()
						)

						.map(entry ->
								new SimpleBoxProjection(
										boxDeviceIds.get(
												entry.getKey()
										),
										entry.getKey(),
										entry.getValue()
								)
						)

						.map(
								UtilityPeriodBoxProjection.class::cast
						)

						.toList();

		return new UtilityData(
				trendRows,
				boxRows,
				List.copyOf(source)
		);
	}

	private List<UtilityPeriodTrendProjection> loadTrend(
			String utilityType,
			String fac,
			LocalDateTime fromTime,
			LocalDateTime toTime
	) {

		return switch (utilityType) {

			case "ELECTRICITY" -> repo.getElectricityTrend(
					fac,
					fromTime,
					toTime
			);

			case "WATER" -> repo.getWaterTrend(
					fac,
					fromTime,
					toTime
			);

			case "AIR" -> repo.getAirTrend(
					fac,
					fromTime,
					toTime
			);

			default -> throw new IllegalArgumentException(
					"Unsupported utility type: "
							+ utilityType
			);
		};
	}

	private List<UtilityPeriodTrendDto> buildTrend(
			List<UtilityPeriodTrendProjection> source
	) {

		if (source == null ||
				source.isEmpty()) {

			return List.of();
		}

		return source
				.stream()

				.filter(
						Objects::nonNull
				)

				.filter(
						row ->
								row.getRecordDate() != null
				)

				.map(
						row ->
								new UtilityPeriodTrendDto(
										row.getRecordDate(),
										zero(
												row.getValue()
										)
								)
				)

				.sorted(
						Comparator.comparing(
								UtilityPeriodTrendDto::date
						)
				)

				.toList();
	}
	// ============================================================
	// LOAD TREND
	//
	// Previous period chỉ cần Trend.
	// ============================================================

	private BigDecimal aggregateTrend(
			List<UtilityPeriodTrendProjection> source,
			boolean sumMode
	) {

		if (source == null ||
				source.isEmpty()) {

			return BigDecimal.ZERO;
		}

		final List<BigDecimal> values =
				source
						.stream()

						.filter(
								Objects::nonNull
						)

						.map(
								row ->
										row.getValue()
						)

						.filter(
								Objects::nonNull
						)

						.toList();

		return aggregateValues(
				values,
				sumMode
		);
	}

	// ============================================================
	// BUILD TREND
	// ============================================================

	private List<UtilityPeriodBoxDto> buildByBox(
			List<UtilityPeriodBoxProjection> source,
			BigDecimal total,
			boolean sumMode
	) {

		if (source == null ||
				source.isEmpty()) {

			return List.of();
		}

		final List<UtilityPeriodBoxDto> result =
				new ArrayList<>();

		// ========================================================
		// SUM MODE
		//
		// Điện:
		// sharePercent có ý nghĩa.
		//
		// Water/Air:
		// sharePercent không có ý nghĩa với °C/bar.
		// trả 0.
		// ========================================================

		for (UtilityPeriodBoxProjection row : source) {

			if (row == null) {
				continue;
			}

			final String boxId =
					normalizeBoxId(
							row.getBoxId()
					);

			if (boxId == null) {
				continue;
			}

			final BigDecimal value =
					zero(
							row.getValue()
					);

			BigDecimal sharePercent =
					BigDecimal.ZERO;

			if (sumMode &&
					total != null &&
					total.compareTo(
							BigDecimal.ZERO
					) > 0) {

				sharePercent =
						value
								.multiply(
										BigDecimal.valueOf(100)
								)
								.divide(
										total,
										2,
										RoundingMode.HALF_UP
								);
			}

			result.add(
					new UtilityPeriodBoxDto(
							row.getBoxDeviceId(),
							boxId,
							value,
							sharePercent
					)
			);
		}

		result.sort(
				Comparator
						.comparing(
								UtilityPeriodBoxDto::total,
								Comparator.nullsLast(
										Comparator.naturalOrder()
								)
						)
						.reversed()
		);

		return List.copyOf(
				result
		);
	}

	// ============================================================
	// AGGREGATE TREND
	//
	// Electricity -> SUM
	//
	// Water/Air -> AVG
	// ============================================================

	private HeatmapResult buildHeatmap(
			List<UtilityPeriodBoxDailyProjection> source,
			DateRange range,
			String period,
			boolean sumMode
	) {

		if ("MONTH".equals(period)) {

			return buildMonthlyHeatmap(
					source,
					range,
					sumMode
			);
		}

		return buildWeeklyHeatmap(
				source,
				range,
				sumMode
		);
	}

	// ============================================================
	// BY BOX
	// ============================================================

	private HeatmapResult buildWeeklyHeatmap(
			List<UtilityPeriodBoxDailyProjection> source,
			DateRange range,
			boolean sumMode
	) {

		final List<LocalDate> dates =
				range
						.from()
						.datesUntil(
								range.toExclusive()
						)
						.toList();

		final List<String> columns =
				dates
						.stream()
						.map(
								this::formatDayMonth
						)
						.toList();

		// ========================================================
		// BOX -> DATE -> VALUE
		// ========================================================

		final Map<
				String,
				Map<LocalDate, BigDecimal>
				> matrix =
				new HashMap<>();

		if (source != null) {

			for (
					UtilityPeriodBoxDailyProjection row :
					source
			) {

				if (row == null) {
					continue;
				}

				final String boxId =
						normalizeBoxId(
								row.getBoxId()
						);

				final LocalDate recordDate =
						row.getRecordDate();

				if (boxId == null ||
						recordDate == null) {

					continue;
				}

				if (recordDate.isBefore(
						range.from()
				)) {
					continue;
				}

				if (!recordDate.isBefore(
						range.toExclusive()
				)) {
					continue;
				}

				final Map<LocalDate, BigDecimal>
						boxValues =
						matrix.computeIfAbsent(
								boxId,
								key ->
										new HashMap<>()
						);

				// Repository đã GROUP BY boxId + date.
				//
				// Vì vậy lý tưởng chỉ có 1 row.
				// put() tránh cộng nhầm pressure/temp.

				boxValues.put(
						recordDate,
						zero(
								row.getValue()
						)
				);
			}
		}

		// ========================================================
		// SORT BOX
		// ========================================================

		final Map<String, BigDecimal> boxScores =
				new HashMap<>();

		for (
				Map.Entry<
						String,
						Map<LocalDate, BigDecimal>
						> entry :
				matrix.entrySet()
		) {

			boxScores.put(
					entry.getKey(),

					aggregateValues(
							entry
									.getValue()
									.values(),

							sumMode
					)
			);
		}

		final List<String> sortedBoxes =
				sortBoxesByTotal(
						matrix.keySet(),
						boxScores
				);

		// ========================================================
		// ROWS
		// ========================================================

		final List<UtilityPeriodBoxTrendDto> rows =
				new ArrayList<>();

		for (String boxId : sortedBoxes) {

			final Map<LocalDate, BigDecimal> dailyValues =
					matrix.getOrDefault(
							boxId,
							Map.of()
					);

			final List<BigDecimal> values =
					new ArrayList<>(
							dates.size()
					);

			for (LocalDate currentDate : dates) {

				values.add(
						dailyValues.getOrDefault(
								currentDate,
								BigDecimal.ZERO
						)
				);
			}

			final BigDecimal rowTotal =
					aggregateNonZeroValues(
							values,
							sumMode
					);

			rows.add(
					new UtilityPeriodBoxTrendDto(
							boxId,
							values,
							rowTotal
					)
			);
		}

		// ========================================================
		// COLUMN TOTAL / AVG
		// ========================================================

		final List<BigDecimal> columnTotals =
				calculateColumns(
						rows,
						dates.size(),
						sumMode
				);

		// ========================================================
		// GRAND
		// ========================================================

		final BigDecimal grandTotal =
				aggregateNonZeroValues(
						columnTotals,
						sumMode
				);

		return new HeatmapResult(
				List.copyOf(columns),
				List.copyOf(rows),
				List.copyOf(columnTotals),
				grandTotal
		);
	}

	// ============================================================
	// HEATMAP
	// ============================================================

	private HeatmapResult buildMonthlyHeatmap(
			List<UtilityPeriodBoxDailyProjection> source,
			DateRange range,
			boolean sumMode
	) {

		final LocalDate monthStart =
				range.from();

		final LocalDate monthEnd =
				range
						.toExclusive()
						.minusDays(1);

		final int daysInMonth =
				monthEnd
						.getDayOfMonth();

		final int weekCount =
				(
						daysInMonth
								+ 6
				) / 7;

		// ========================================================
		// COLUMN
		// ========================================================

		final List<String> columns =
				new ArrayList<>(
						weekCount
				);

		for (
				int week = 1;
				week <= weekCount;
				week++
		) {

			columns.add(
					"W" + week
			);
		}

		// ========================================================
		// MATRIX
		//
		// boxId
		//   ↓
		// weekIndex
		//   ↓
		// list daily values
		//
		// List là cần thiết để AVG Water/Air.
		// ========================================================

		final Map<
				String,
				Map<Integer, List<BigDecimal>>
				> matrix =
				new HashMap<>();

		if (source != null) {

			for (
					UtilityPeriodBoxDailyProjection row :
					source
			) {

				if (row == null) {
					continue;
				}

				final String boxId =
						normalizeBoxId(
								row.getBoxId()
						);

				final LocalDate recordDate =
						row.getRecordDate();

				if (boxId == null ||
						recordDate == null) {

					continue;
				}

				if (recordDate.isBefore(
						monthStart
				)) {
					continue;
				}

				if (!recordDate.isBefore(
						range.toExclusive()
				)) {
					continue;
				}

				final int weekIndex =
						(
								recordDate
										.getDayOfMonth()
										- 1
						) / 7;

				matrix
						.computeIfAbsent(
								boxId,
								key ->
										new HashMap<>()
						)
						.computeIfAbsent(
								weekIndex,
								key ->
										new ArrayList<>()
						)
						.add(
								zero(
										row.getValue()
								)
						);
			}
		}

		// ========================================================
		// SCORE PER BOX
		// ========================================================

		final Map<String, BigDecimal> boxScores =
				new HashMap<>();

		for (
				Map.Entry<
						String,
						Map<Integer, List<BigDecimal>>
						> entry :
				matrix.entrySet()
		) {

			final List<BigDecimal> allValues =
					new ArrayList<>();

			for (
					List<BigDecimal> weekValues :
					entry
							.getValue()
							.values()
			) {

				allValues.addAll(
						weekValues
				);
			}

			boxScores.put(
					entry.getKey(),

					aggregateNonZeroValues(
							allValues,
							sumMode
					)
			);
		}

		// ========================================================
		// SORT
		// ========================================================

		final List<String> sortedBoxes =
				sortBoxesByTotal(
						matrix.keySet(),
						boxScores
				);

		// ========================================================
		// ROWS
		// ========================================================

		final List<UtilityPeriodBoxTrendDto> rows =
				new ArrayList<>();

		for (String boxId : sortedBoxes) {

			final Map<Integer, List<BigDecimal>>
					weekValues =
					matrix.getOrDefault(
							boxId,
							Map.of()
					);

			final List<BigDecimal> values =
					new ArrayList<>(
							weekCount
					);

			for (
					int weekIndex = 0;
					weekIndex < weekCount;
					weekIndex++
			) {

				final List<BigDecimal> sourceValues =
						weekValues.getOrDefault(
								weekIndex,
								List.of()
						);

				values.add(
						aggregateNonZeroValues(
								sourceValues,
								sumMode
						)
				);
			}

			final BigDecimal rowTotal =
					aggregateNonZeroValues(
							values,
							sumMode
					);

			rows.add(
					new UtilityPeriodBoxTrendDto(
							boxId,
							values,
							rowTotal
					)
			);
		}

		// ========================================================
		// COLUMN VALUE
		// ========================================================

		final List<BigDecimal> columnTotals =
				calculateColumns(
						rows,
						weekCount,
						sumMode
				);

		final BigDecimal grandTotal =
				aggregateNonZeroValues(
						columnTotals,
						sumMode
				);

		return new HeatmapResult(
				List.copyOf(columns),
				List.copyOf(rows),
				List.copyOf(columnTotals),
				grandTotal
		);
	}

	// ============================================================
	// WEEK HEATMAP
	//
	// Repository đã aggregate:
	//
	// Electricity:
	// box/day -> SUM
	//
	// Water/Air:
	// box/day -> AVG
	// ============================================================

	private List<BigDecimal> calculateColumns(
			List<UtilityPeriodBoxTrendDto> rows,
			int columnCount,
			boolean sumMode
	) {

		if (rows == null ||
				rows.isEmpty() ||
				columnCount <= 0) {

			return List.of();
		}

		final List<BigDecimal> result =
				new ArrayList<>(
						columnCount
				);

		for (
				int column = 0;
				column < columnCount;
				column++
		) {

			final List<BigDecimal> values =
					new ArrayList<>();

			for (
					UtilityPeriodBoxTrendDto row :
					rows
			) {

				if (row == null ||
						row.values() == null ||
						column >= row.values().size()) {

					continue;
				}

				final BigDecimal value =
						row
								.values()
								.get(column);

				if (value == null ||
						value.compareTo(
								BigDecimal.ZERO
						) <= 0) {

					continue;
				}

				values.add(
						value
				);
			}

			result.add(
					aggregateValues(
							values,
							sumMode
					)
			);
		}

		return result;
	}

	// ============================================================
	// MONTH HEATMAP
	//
	// W1 = 01 - 07
	// W2 = 08 - 14
	// W3 = 15 - 21
	// W4 = 22 - 28
	// W5 = 29 - END
	//
	// Electricity -> SUM
	// Water/Air   -> AVG
	// ============================================================

	private BigDecimal aggregateValues(
			Collection<BigDecimal> values,
			boolean sumMode
	) {

		if (values == null ||
				values.isEmpty()) {

			return BigDecimal.ZERO;
		}

		final List<BigDecimal> valid =
				values
						.stream()
						.filter(
								Objects::nonNull
						)
						.toList();

		if (valid.isEmpty()) {
			return BigDecimal.ZERO;
		}

		final BigDecimal sum =
				valid
						.stream()
						.reduce(
								BigDecimal.ZERO,
								BigDecimal::add
						);

		if (sumMode) {
			return sum;
		}

		return sum.divide(
				BigDecimal.valueOf(
						valid.size()
				),
				2,
				RoundingMode.HALF_UP
		);
	}

	// ============================================================
	// CALCULATE HEATMAP COLUMNS
	//
	// Electricity -> SUM box
	// Water/Air   -> AVG box
	// ============================================================

	private BigDecimal aggregateNonZeroValues(
			Collection<BigDecimal> values,
			boolean sumMode
	) {

		if (values == null ||
				values.isEmpty()) {

			return BigDecimal.ZERO;
		}

		final List<BigDecimal> valid =
				values
						.stream()

						.filter(
								Objects::nonNull
						)

						.filter(
								value ->
										value.compareTo(
												BigDecimal.ZERO
										) > 0
						)

						.toList();

		return aggregateValues(
				valid,
				sumMode
		);
	}

	// ============================================================
	// AGGREGATE
	// ============================================================

	private List<String> sortBoxesByTotal(
			Set<String> boxes,
			Map<String, BigDecimal> totals
	) {

		if (boxes == null ||
				boxes.isEmpty()) {

			return List.of();
		}

		final List<String> result =
				new ArrayList<>(
						boxes
				);

		result.sort(
				Comparator
						.comparing(
								(
										String boxId
								) ->
										totals
												.getOrDefault(
														boxId,
														BigDecimal.ZERO
												)
						)
						.reversed()
						.thenComparing(
								Comparator.naturalOrder()
						)
		);

		return result;
	}

	// ============================================================
	// AGGREGATE IGNORE ZERO
	//
	// Zero trong Heatmap có thể là "không có dữ liệu".
	// Không nên kéo AVG Water/Air xuống.
	// ============================================================

	private BigDecimal calculateChangePercent(
			BigDecimal current,
			BigDecimal previous
	) {

		final BigDecimal safeCurrent =
				zero(
						current
				);

		if (previous == null ||
				previous.compareTo(
						BigDecimal.ZERO
				) == 0) {

			return null;
		}

		return safeCurrent
				.subtract(
						previous
				)
				.multiply(
						BigDecimal.valueOf(
								100
						)
				)
				.divide(
						previous,
						2,
						RoundingMode.HALF_UP
				);
	}

	// ============================================================
	// SORT BOX DESC
	// ============================================================

	private DateRange resolveRange(
			LocalDate date,
			String period
	) {

		// ============================================================
		// MONTH
		// ============================================================

		if ("MONTH".equals(period)) {

			final LocalDate from =
					date.withDayOfMonth(1);

			return new DateRange(
					from,
					from.plusMonths(1)
			);
		}

		// ============================================================
		// WEEK = 7 DAYS
		//
		// date là ngày cuối.
		//
		// Ví dụ:
		// date = 2026-08-17
		//
		// from        = 2026-08-11
		// toExclusive = 2026-08-18
		//
		// => 11 -> 17 = đúng 7 ngày
		// ============================================================

		final LocalDate from =
				date.minusDays(6);

		final LocalDate toExclusive =
				date.plusDays(1);

		return new DateRange(
				from,
				toExclusive
		);
	}

	// ============================================================
	// CHANGE %
	// ============================================================

	private DateRange resolvePreviousRange(
			DateRange current,
			String period
	) {

		if ("MONTH".equals(period)) {

			final LocalDate previousStart =
					current
							.from()
							.minusMonths(
									1
							);

			return new DateRange(
					previousStart,
					current.from()
			);
		}

		return new DateRange(
				current
						.from()
						.minusDays(
								7
						),

				current.from()
		);
	}
	// ============================================================
	// RANGE
	// ============================================================

	private LocalDate parseDate(
			String value
	) {

		if (value == null ||
				value.isBlank()) {

			return LocalDate.now();
		}

		final String normalized =
				value
						.trim()
						.replace(
								"-",
								""
						)
						.replace(
								"/",
								""
						);

		if (!normalized.matches(
				"\\d{8}"
		)) {

			throw new IllegalArgumentException(
					"date must be yyyyMMdd. Example: 20260812"
			);
		}

		final int year =
				Integer.parseInt(
						normalized.substring(
								0,
								4
						)
				);

		final int month =
				Integer.parseInt(
						normalized.substring(
								4,
								6
						)
				);

		final int day =
				Integer.parseInt(
						normalized.substring(
								6,
								8
						)
				);

		return LocalDate.of(
				year,
				month,
				day
		);
	}

	// ============================================================
	// PREVIOUS RANGE
	// ============================================================

	private String normalizeFac(
			String fac
	) {

		if (fac == null ||
				fac.isBlank()) {

			return "KVH";
		}

		final String value =
				fac.trim();

		if (value.equalsIgnoreCase(
				"FAC_A"
		)) {
			return "Fac_A";
		}

		if (value.equalsIgnoreCase(
				"FAC_B"
		)) {
			return "Fac_B";
		}

		if (value.equalsIgnoreCase(
				"FAC_C"
		)) {
			return "Fac_C";
		}

		if (value.equalsIgnoreCase(
				"KVH"
		)) {
			return "KVH";
		}

		return value;
	}

	// ============================================================
	// PARSE DATE
	// ============================================================

	private String normalizeType(
			String type
	) {

		if (type == null ||
				type.isBlank()) {

			return "ELECTRICITY";
		}

		final String value =
				type
						.trim()
						.toUpperCase(
								Locale.ROOT
						);

		if (!Set.of(
				"ELECTRICITY",
				"WATER",
				"AIR"
		).contains(
				value
		)) {

			throw new IllegalArgumentException(
					"type must be ELECTRICITY, WATER or AIR"
			);
		}

		return value;
	}

	// ============================================================
	// NORMALIZE FAC
	// ============================================================

	private String normalizePeriod(
			String period
	) {

		if (period == null ||
				period.isBlank()) {

			return "WEEK";
		}

		final String value =
				period
						.trim()
						.toUpperCase(
								Locale.ROOT
						);

		if (!"WEEK".equals(value) &&
				!"MONTH".equals(value)) {

			throw new IllegalArgumentException(
					"period must be WEEK or MONTH"
			);
		}

		return value;
	}

	// ============================================================
	// NORMALIZE TYPE
	// ============================================================

	private String normalizeBoxId(
			String boxId
	) {

		if (boxId == null) {
			return null;
		}

		final String value =
				boxId.trim();

		return value.isEmpty()
				? null
				: value;
	}

	// ============================================================
	// NORMALIZE PERIOD
	// ============================================================

	private String formatDayMonth(
			LocalDate date
	) {

		if (date == null) {
			return "";
		}

		return String.format(
				"%02d/%02d",
				date.getDayOfMonth(),
				date.getMonthValue()
		);
	}

	// ============================================================
	// BOX ID
	// ============================================================

	private String resolveUnit(
			List<UtilityPeriodTrendProjection> rows
	) {

		if (rows == null ||
				rows.isEmpty()) {

			return "";
		}

		return rows
				.stream()
				.filter(Objects::nonNull)

				.map(
						UtilityPeriodTrendProjection::getUnit
				)

				.filter(Objects::nonNull)

				.map(String::trim)

				.filter(
						value ->
								!value.isEmpty()
				)

				.findFirst()

				.orElse("");
	}

	// ============================================================
	// DD/MM
	// ============================================================

	private BigDecimal zero(
			BigDecimal value
	) {

		return value == null
				? BigDecimal.ZERO
				: value;
	}

	// ============================================================
	// UNIT FALLBACK
	//
	// Tạm thời thôi.
	// Phần dưới mình chỉ cách lấy từ DB.
	// ============================================================

// ============================================================
// RESOLVE UNIT FROM DATABASE
//
// Unit lấy trực tiếp từ UtilityPeriodTrendProjection.
//
// Ví dụ:
// Electricity -> kWh
// Water       -> °C
// Air         -> bar
//
// Không hard-code trong service.
// ============================================================

	private record SimpleTrendProjection(
			LocalDate recordDate,
			BigDecimal value,
			String unit
	) implements UtilityPeriodTrendProjection {

		@Override
		public LocalDate getRecordDate() {
			return recordDate;
		}

		@Override
		public BigDecimal getValue() {
			return value;
		}

		@Override
		public String getUnit() {
			return unit;
		}
	}

	// ============================================================
	// NULL -> ZERO
	// ============================================================

	private record SimpleBoxProjection(
			String boxDeviceId,
			String boxId,
			BigDecimal value
	) implements UtilityPeriodBoxProjection {

		@Override
		public String getBoxDeviceId() {
			return boxDeviceId;
		}

		@Override
		public String getBoxId() {
			return boxId;
		}

		@Override
		public BigDecimal getValue() {
			return value;
		}
	}

	// ============================================================
	// DATA RECORD
	// ============================================================

	private record UtilityData(

			List<UtilityPeriodTrendProjection>
			trendRows,

			List<UtilityPeriodBoxProjection>
			boxRows,

			List<UtilityPeriodBoxDailyProjection>
			boxDailyRows

	) {
	}

	// ============================================================
	// RANGE RECORD
	// ============================================================

	private record DateRange(
			LocalDate from,
			LocalDate toExclusive
	) {
	}

	// ============================================================
	// HEATMAP RECORD
	// ============================================================

	private record HeatmapResult(

			List<String> columns,

			List<UtilityPeriodBoxTrendDto> rows,

			List<BigDecimal> columnTotals,

			BigDecimal grandTotal

	) {
	}

	private record PeriodComparisonRange(

			LocalDateTime currentFrom,

			LocalDateTime currentTo,

			LocalDateTime previousFrom,

			LocalDateTime previousTo

	) {
	}
}