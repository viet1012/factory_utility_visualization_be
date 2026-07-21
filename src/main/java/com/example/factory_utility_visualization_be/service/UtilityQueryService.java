package com.example.factory_utility_visualization_be.service;


import com.example.factory_utility_visualization_be.dto.*;
import com.example.factory_utility_visualization_be.dto.latest.*;
import com.example.factory_utility_visualization_be.dto.mapper.HourPointView;
import com.example.factory_utility_visualization_be.dto.mapper.LatestRecordView;
import com.example.factory_utility_visualization_be.dto.mapper.MinutePointView;
import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import com.example.factory_utility_visualization_be.model.F2UtilityScada;
import com.example.factory_utility_visualization_be.model.F2UtilityScadaChannel;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaHistoryRepo;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaRepo;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaChannelRepo;
import com.example.factory_utility_visualization_be.repository.F2UtilityScadaRepo;
import com.example.factory_utility_visualization_be.request.UtilitySeriesRequest;
import com.example.factory_utility_visualization_be.response.UtilitySeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilityQueryService {

	private final F2UtilityScadaRepo scadaRepo;
	private final F2UtilityScadaChannelRepo channelRepo;
	private final F2UtilityParaRepo paraRepo;
	private final F2UtilityParaHistoryRepo historyRepo;

	// HELPER
	public static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}

	// 1) GET /scadas
	public List<ScadaDto> getScadas() {
		return scadaRepo.findAll().stream().map(s -> ScadaDto.builder().scadaId(s.getScadaId()).fac(s.getFac()).plcIp(s.getPlcIp()).plcPort(s.getPlcPort()).wlan(s.getWlan()).build()).toList();
	}

	// 2) GET /channels?facId&scadaId&cate
	public List<ChannelDto> getChannels(String facId, String scadaId, String cate) {

		final Set<String> allowedScadaIds = (facId != null && !facId.isBlank()) ? scadaRepo.findAll().stream().filter(s -> facId.equalsIgnoreCase(s.getFac())).map(F2UtilityScada::getScadaId).collect(Collectors.toSet()) : null;

		if (allowedScadaIds != null && allowedScadaIds.isEmpty()) return List.of();

		List<F2UtilityScadaChannel> channels = channelRepo.findAll();

		if (allowedScadaIds != null) {
			channels = channels.stream().filter(c -> allowedScadaIds.contains(c.getScadaId())).toList();
		}
		if (scadaId != null && !scadaId.isBlank()) {
			final String scadaIdF = scadaId; // (optional) cho chắc
			channels = channels.stream().filter(c -> scadaIdF.equalsIgnoreCase(c.getScadaId())).toList();
		}
		if (cate != null && !cate.isBlank()) {
			final String cateF = cate; // (optional)
			channels = channels.stream().filter(c -> cateF.equalsIgnoreCase(c.getCate())).toList();
		}

		return channels.stream().map(c -> ChannelDto.builder().id(c.getId()).scadaId(c.getScadaId()).cate(c.getCate()).boxDeviceId(c.getBoxDeviceId()).boxId(c.getBoxId()).build()).toList();
	}

	// 3) GET /params?boxDeviceId&cate&facId
	public List<ParamDto> getParams(String boxDeviceId, String cate, String facId, Integer importantOnly // 0/1, null allowed
	) {
		int important = (importantOnly == null) ? 0 : importantOnly; // default 0

		List<F2UtilityPara> ps = paraRepo.searchParams(blankToNull(boxDeviceId), blankToNull(cate), null, blankToNull(facId), important);

		Map<String, F2UtilityScadaChannel> chByDevice = channelRepo.findAll().stream().collect(Collectors.toMap(F2UtilityScadaChannel::getBoxDeviceId, x -> x, (a, b) -> a));

//		Map<String, F2UtilityScada> scadaById = scadaRepo.findAll().stream()
//				.collect(Collectors.toMap(F2UtilityScada::getScadaId, x -> x));
		Map<String, F2UtilityScada> scadaById = scadaRepo.findAll().stream().collect(Collectors.toMap(F2UtilityScada::getScadaId, x -> x, (a, b) -> a   // 👈 FIX
		));
		return ps.stream().map(p -> {
			var ch = chByDevice.get(p.getBoxDeviceId());
			var sc = (ch == null) ? null : scadaById.get(ch.getScadaId());

			return ParamDto.builder().id(p.getId()).boxDeviceId(p.getBoxDeviceId()).plcAddress(p.getPlcAddress()).valueType(p.getValueType()).unit(p.getUnit()).category(p.getCateId()).nameVi(p.getNameVi()).nameEn(p.getNameEn()).isImportant(p.getIsImportant()).isAlert(p.getIsAlert()).scadaId(ch == null ? null : ch.getScadaId()).fac(sc == null ? null : sc.getFac()).cate(ch == null ? null : ch.getCate()).boxId(ch == null ? null : ch.getBoxId()).build();
		}).toList();
	}

	// 5) GET /latest/one?boxDeviceId=...&plcAddress=...
	public LatestRecordDto getLatestOne(String boxDeviceId, String plcAddress) {
		var h = historyRepo.findTopByBoxDeviceIdAndPlcAddressOrderByRecordedAtDesc(boxDeviceId, plcAddress).orElseThrow(() -> new NoSuchElementException("No record for " + boxDeviceId + " / " + plcAddress));

		var ch = channelRepo.findByBoxDeviceId(boxDeviceId).stream().findFirst().orElse(null);
		var sc = (ch == null) ? null : scadaRepo.findByScadaId(ch.getScadaId()).orElse(null);

		return LatestRecordDto.builder().boxDeviceId(h.getBoxDeviceId()).plcAddress(h.getPlcAddress()).value(h.getValue()).recordedAt(h.getRecordedAt()).scadaId(ch == null ? null : ch.getScadaId()).fac(sc == null ? null : sc.getFac()).cate(ch == null ? null : ch.getCate()).boxId(ch == null ? null : ch.getBoxId()).build();
	}


	public List<LatestFacilityDto> getLatest(
			String facId,
			String scadaId,
			String cate,
			String boxDeviceId,
			List<String> cateIds
	) {
		String normalizedFac = blankToNull(facId);
		String normalizedScada = blankToNull(scadaId);
		String normalizedCate = blankToNull(cate);
		String normalizedDevice = blankToNull(boxDeviceId);

		List<ChannelDto> channelDtos = getChannels(
				normalizedFac,
				normalizedScada,
				normalizedCate
		);

		Set<String> filteredDeviceIds = channelDtos.stream()
				.map(ChannelDto::getBoxDeviceId)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));

		final List<String> deviceIds;

		if (normalizedDevice != null) {
			if (!filteredDeviceIds.isEmpty()
					&& !filteredDeviceIds.contains(normalizedDevice)) {
				return List.of();
			}

			deviceIds = List.of(normalizedDevice);
		} else {
			deviceIds = new ArrayList<>(filteredDeviceIds);
		}

		boolean hasChannelFilter =
				normalizedFac != null
						|| normalizedScada != null
						|| normalizedCate != null;

		if (deviceIds.isEmpty() && hasChannelFilter) {
			return List.of();
		}

		int useDeviceIds = deviceIds.isEmpty() ? 0 : 1;

		List<String> safeDeviceIds = useDeviceIds == 1
				? deviceIds
				: List.of("__NO_DEVICE__");

		List<String> normalizedCateIds = normalizeStringList(cateIds);

		int useCateIds = normalizedCateIds.isEmpty() ? 0 : 1;

		List<String> safeCateIds = useCateIds == 1
				? normalizedCateIds
				: List.of("__NO_CATE__");

		List<LatestRecordView> rows = historyRepo.latestPerKey(
				normalizedFac,
				normalizedScada,
				normalizedCate,
				normalizedDevice,
				useDeviceIds,
				safeDeviceIds,
				useCateIds,
				safeCateIds
		);

		return buildLatestTree(rows);
	}

	private List<String> normalizeStringList(List<String> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}

		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isEmpty())
				.distinct()
				.toList();
	}

	private List<LatestFacilityDto> buildLatestTree(
			List<LatestRecordView> rows
	) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}

		Map<String, Map<String, Map<String, Map<String,
				Map<String, List<LatestRecordView>>>>>> grouped =
				rows.stream()
						.collect(
								Collectors.groupingBy(
										row -> safeText(row.getFac()),
										LinkedHashMap::new,
										Collectors.groupingBy(
												row -> safeText(row.getCate()),
												LinkedHashMap::new,
												Collectors.groupingBy(
														row -> safeText(
																row.getScadaId()
														),
														LinkedHashMap::new,
														Collectors.groupingBy(
																row -> safeText(
																		row.getBoxId()
																),
																LinkedHashMap::new,
																Collectors.groupingBy(
																		row -> safeText(
																				row.getBoxDeviceId()
																		),
																		LinkedHashMap::new,
																		Collectors.toList()
																)
														)
												)
										)
								)
						);

		return grouped.entrySet()
				.stream()
				.map(facEntry -> LatestFacilityDto.builder()
						.fac(facEntry.getKey())
						.categories(
								facEntry.getValue()
										.entrySet()
										.stream()
										.map(cateEntry ->
												LatestCategoryDto.builder()
														.cate(cateEntry.getKey())
														.scadas(
																cateEntry.getValue()
																		.entrySet()
																		.stream()
																		.map(scadaEntry ->
																				LatestScadaDto.builder()
																						.scadaId(
																								scadaEntry.getKey()
																						)
																						.boxes(
																								scadaEntry.getValue()
																										.entrySet()
																										.stream()
																										.map(boxEntry ->
																												LatestBoxDto.builder()
																														.boxId(
																																boxEntry.getKey()
																														)
																														.devices(
																																boxEntry.getValue()
																																		.entrySet()
																																		.stream()
																																		.map(deviceEntry ->
																																				LatestDeviceDto.builder()
																																						.boxDeviceId(
																																								deviceEntry.getKey()
																																						)
																																						.signals(
																																								deviceEntry.getValue()
																																										.stream()
																																										.map(this::toLatestSignal)
																																										.sorted(
																																												Comparator.comparing(
																																														LatestSignalDto::getPlcAddress,
																																														Comparator.nullsLast(
																																																this::comparePlcAddress
																																														)
																																												)
																																										)
																																										.toList()
																																						)
																																						.build()
																																		)
																																		.toList()
																														)
																														.build()
																										)
																										.toList()
																						)
																						.build()
																		)
																		.toList()
														)
														.build()
										)
										.toList()
						)
						.build()
				)
				.toList();
	}

	private LatestSignalDto toLatestSignal(
			LatestRecordView row
	) {
		return LatestSignalDto.builder()
				.plcAddress(row.getPlcAddress())
				.cateId(row.getCateId())
				.nameEn(row.getNameEn())
				.value(row.getValue())
				.unit(row.getUnit())
				.recordedAt(row.getRecordedAt())
				.build();
	}

	private String safeText(String value) {
		if (value == null || value.isBlank()) {
			return "UNKNOWN";
		}

		return value.trim();
	}

	private int comparePlcAddress(
			String first,
			String second
	) {
		if (first == null && second == null) {
			return 0;
		}

		if (first == null) {
			return 1;
		}

		if (second == null) {
			return -1;
		}

		Pattern pattern = Pattern.compile(
				"^([A-Za-z]+)(\\d+)$"
		);

		Matcher firstMatcher = pattern.matcher(first);
		Matcher secondMatcher = pattern.matcher(second);

		if (!firstMatcher.matches()
				|| !secondMatcher.matches()) {
			return first.compareToIgnoreCase(second);
		}

		int prefixCompare = firstMatcher
				.group(1)
				.compareToIgnoreCase(
						secondMatcher.group(1)
				);

		if (prefixCompare != 0) {
			return prefixCompare;
		}

		int firstNumber = Integer.parseInt(
				firstMatcher.group(2)
		);

		int secondNumber = Integer.parseInt(
				secondMatcher.group(2)
		);

		return Integer.compare(
				firstNumber,
				secondNumber
		);
	}

	public List<LatestRecordDto> getLatest1(String facId, String scadaId, String cate, String boxDeviceId, List<String> cateIds) {
		List<ChannelDto> channelDtos = getChannels(facId, scadaId, cate);

		Set<String> filteredDeviceIds = channelDtos.stream().map(ChannelDto::getBoxDeviceId).collect(Collectors.toSet());

		final List<String> deviceIds;

		if (boxDeviceId != null && !boxDeviceId.isBlank()) {
			if (!filteredDeviceIds.isEmpty() && !filteredDeviceIds.contains(boxDeviceId)) {
				return List.of();
			}
			deviceIds = List.of(boxDeviceId);
		} else {
			deviceIds = filteredDeviceIds.isEmpty() ? List.of() : new ArrayList<>(filteredDeviceIds);
		}

		// nếu user filter fac/scada/cate mà không ra device nào
		if (deviceIds.isEmpty() && (facId != null || scadaId != null || cate != null)) {
			return List.of();
		}

		int useDeviceIds = deviceIds.isEmpty() ? 0 : 1;
		List<String> safeDeviceIds = useDeviceIds == 1 ? deviceIds : List.of("__NO_DEVICE__");

		List<String> cateIdsNorm = cateIds == null ? List.of() : cateIds.stream().filter(s -> s != null && !s.isBlank()).toList();

		int useCateIds = cateIdsNorm.isEmpty() ? 0 : 1;
		List<String> safeCateIds = useCateIds == 1 ? cateIdsNorm : List.of("__NO_CATE__");

		var rows = historyRepo.latestPerKey(facId, scadaId, cate, blankToNull(boxDeviceId),

				useDeviceIds, safeDeviceIds,

				useCateIds, safeCateIds);

		return rows.stream().map(r -> LatestRecordDto.builder().boxDeviceId(r.getBoxDeviceId()).plcAddress(r.getPlcAddress()).value(r.getValue()).recordedAt(r.getRecordedAt()).cateId(r.getCateId()).scadaId(r.getScadaId()).fac(r.getFac()).cate(r.getCate()).boxId(r.getBoxId()).name_en(r.getNameEn()).unit(r.getUnit()).minVol(r.getMinVol()).maxVol(r.getMaxVol()).minVolStd(r.getMinVolStd()).maxVolStd(r.getMaxVolStd()).alarm(r.getAlarm() == null ? "Normal" : r.getAlarm()).build()).toList();
	}


	public List<MinutePointView> getSeriesByMinute(LocalDateTime fromTs, LocalDateTime toTs, String facId,
	                                               String cate,
	                                               String boxDeviceId,
	                                               String plcAddress,
	                                               List<String> cateIds) {
		// normalize cateIds
		List<String> cateIdsNorm = (cateIds == null) ? List.of() : cateIds.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();

		int useCateIds = cateIdsNorm.isEmpty() ? 0 : 1;

		// SQL Server nativeQuery + IN (:cateIds) thường không thích list rỗng
		List<String> safeCateIds = (useCateIds == 1) ? cateIdsNorm : List.of("__NO_CATE__");
		return historyRepo.seriesByMinuteLast(fromTs, toTs, blankToNull(boxDeviceId), blankToNull(plcAddress),

				blankToNull(facId),     // ✅ NEW
				blankToNull(cate),      // ✅ NEW

				useCateIds, safeCateIds);
	}



	public List<HourPointDto> seriesHourlySum(LocalDateTime fromTs, LocalDateTime toTs, String fac, String scadaId, String cate, String boxDeviceId, String plcAddress, String cateId,          // single
	                                          List<String> cateIds    // list
	) {
		// cateId single -> merge into cateIds
		List<String> mergedCateIds = new ArrayList<>();
		if (cateIds != null) mergedCateIds.addAll(cateIds);
		if (cateId != null && !cateId.trim().isEmpty()) mergedCateIds.add(cateId.trim());

		mergedCateIds = mergedCateIds.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

		int useCateIds = mergedCateIds.isEmpty() ? 0 : 1;
		List<String> safeCateIds = useCateIds == 1 ? mergedCateIds : Collections.emptyList();

		List<HourPointView> rows = historyRepo.seriesByHourSum(fromTs, toTs, blankToNull(plcAddress), blankToNull(boxDeviceId), blankToNull(fac), blankToNull(cate), blankToNull(scadaId), useCateIds, safeCateIds);

		return rows.stream().map(v -> HourPointDto.builder().ts(v.getTs()).value(v.getValue()).boxDeviceId(v.getBoxDeviceId()).plcAddress(v.getPlcAddress()).cateId(v.getCateId()).nameEn(v.getNameEn()).nameVi(v.getNameVi()).fac(v.getFac()).cate(v.getCate()).build()).collect(Collectors.toList());
	}

	private double round2(double v) {
		return new java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
	}

	private String fmtPct(Double pct) {
		if (pct == null) return "--";
		// luôn 2 chữ số
		return String.format(java.util.Locale.US, "%.2f%%", pct);
	}

	private String trendFromDelta(double delta, double eps) {
		if (Math.abs(delta) <= eps) return "STABLE";
		return delta > 0 ? "UP" : "DOWN";
	}

}
