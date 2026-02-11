package com.example.factory_utility_visualization_be.service;



import com.example.factory_utility_visualization_be.dto.*;
import com.example.factory_utility_visualization_be.dto.mapper.MinutePointView;
import com.example.factory_utility_visualization_be.model.*;
import com.example.factory_utility_visualization_be.repository.*;
import com.example.factory_utility_visualization_be.request.*;
import com.example.factory_utility_visualization_be.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UtilityQueryService {

    private final F2UtilityScadaRepo scadaRepo;
    private final F2UtilityScadaChannelRepo channelRepo;
    private final F2UtilityParaRepo paraRepo;
    private final F2UtilityParaHistoryRepo historyRepo;

    // 1) GET /scadas
    public List<ScadaDto> getScadas() {
        return scadaRepo.findAll().stream()
                .map(s -> ScadaDto.builder()
                        .scadaId(s.getScadaId())
                        .fac(s.getFac())
                        .plcIp(s.getPlcIp())
                        .plcPort(s.getPlcPort())
                        .wlan(s.getWlan())
                        .build())
                .toList();
    }

    // 2) GET /channels?facId&scadaId&cate
    public List<ChannelDto> getChannels(String facId, String scadaId, String cate) {

        final Set<String> allowedScadaIds = (facId != null && !facId.isBlank())
                ? scadaRepo.findAll().stream()
                .filter(s -> facId.equalsIgnoreCase(s.getFac()))
                .map(F2UtilityScada::getScadaId)
                .collect(Collectors.toSet())
                : null;

        if (allowedScadaIds != null && allowedScadaIds.isEmpty()) return List.of();

        List<F2UtilityScadaChannel> channels = channelRepo.findAll();

        if (allowedScadaIds != null) {
            channels = channels.stream()
                    .filter(c -> allowedScadaIds.contains(c.getScadaId()))
                    .toList();
        }
        if (scadaId != null && !scadaId.isBlank()) {
            final String scadaIdF = scadaId; // (optional) cho chắc
            channels = channels.stream()
                    .filter(c -> scadaIdF.equalsIgnoreCase(c.getScadaId()))
                    .toList();
        }
        if (cate != null && !cate.isBlank()) {
            final String cateF = cate; // (optional)
            channels = channels.stream()
                    .filter(c -> cateF.equalsIgnoreCase(c.getCate()))
                    .toList();
        }

        return channels.stream()
                .map(c -> ChannelDto.builder()
                        .id(c.getId())
                        .scadaId(c.getScadaId())
                        .cate(c.getCate())
                        .boxDeviceId(c.getBoxDeviceId())
                        .boxId(c.getBoxId())
                        .build())
                .toList();
    }

    // 3) GET /params?boxDeviceId&cate&facId
    public List<ParamDto> getParams(String boxDeviceId, String cate, String facId) {
        // cate suy qua channel => repo join đã xử lý
        List<F2UtilityPara> ps = paraRepo.searchParams(
                blankToNull(boxDeviceId),
                blankToNull(cate),
                null,
                blankToNull(facId)
        );

        // enrich thêm scada/fac/cate/boxId cho DTO
        Map<String, F2UtilityScadaChannel> chByDevice = channelRepo.findAll().stream()
                .collect(Collectors.toMap(F2UtilityScadaChannel::getBoxDeviceId, x -> x, (a, b) -> a));
        Map<String, F2UtilityScada> scadaById = scadaRepo.findAll().stream()
                .collect(Collectors.toMap(F2UtilityScada::getScadaId, x -> x));

        return ps.stream().map(p -> {
            var ch = chByDevice.get(p.getBoxDeviceId());
            var sc = (ch == null) ? null : scadaById.get(ch.getScadaId());

            return ParamDto.builder()
                    .id(p.getId())
                    .boxDeviceId(p.getBoxDeviceId())
                    .plcAddress(p.getPlcAddress())
                    .valueType(p.getValueType())
                    .unit(p.getUnit())
                    .category(p.getCategory())
                    .nameVi(p.getNameVi())
                    .nameEn(p.getNameEn())
                    .isImportant(p.getIsImportant())
                    .isAlert(p.getIsAlert())
                    .scadaId(ch == null ? null : ch.getScadaId())
                    .fac(sc == null ? null : sc.getFac())
                    .cate(ch == null ? null : ch.getCate())
                    .boxId(ch == null ? null : ch.getBoxId())
                    .build();
        }).toList();
    }

    // 5) GET /latest/one?boxDeviceId=...&plcAddress=...
    public LatestRecordDto getLatestOne(String boxDeviceId, String plcAddress) {
        var h = historyRepo.findTopByBoxDeviceIdAndPlcAddressOrderByRecordedAtDesc(boxDeviceId, plcAddress)
                .orElseThrow(() -> new NoSuchElementException("No record for " + boxDeviceId + " / " + plcAddress));

        var ch = channelRepo.findByBoxDeviceId(boxDeviceId).stream().findFirst().orElse(null);
        var sc = (ch == null) ? null : scadaRepo.findById(ch.getScadaId()).orElse(null);

        return LatestRecordDto.builder()
                .boxDeviceId(h.getBoxDeviceId())
                .plcAddress(h.getPlcAddress())
                .value(h.getValue())
                .recordedAt(h.getRecordedAt())
                .scadaId(ch == null ? null : ch.getScadaId())
                .fac(sc == null ? null : sc.getFac())
                .cate(ch == null ? null : ch.getCate())
                .boxId(ch == null ? null : ch.getBoxId())
                .build();
    }

    public List<LatestRecordDto> getLatest(
            String facId,
            String scadaId,
            String cate,
            String boxDeviceId,
            List<String> cateIds
    ) {
        List<ChannelDto> channelDtos = getChannels(facId, scadaId, cate);
        Set<String> filteredDeviceIds = channelDtos.stream()
                .map(ChannelDto::getBoxDeviceId)
                .collect(Collectors.toSet());

        final List<String> deviceIds;
        if (boxDeviceId != null && !boxDeviceId.isBlank()) {
            if (!filteredDeviceIds.isEmpty() && !filteredDeviceIds.contains(boxDeviceId)) return List.of();
            deviceIds = List.of(boxDeviceId);
        } else {
            deviceIds = filteredDeviceIds.isEmpty() ? List.of() : new ArrayList<>(filteredDeviceIds);
        }

        // nếu user filter fac/scada/cate mà không ra device nào
        if (deviceIds.isEmpty() && (facId != null || scadaId != null || cate != null)) return List.of();

        // flags
        int useDeviceIds = deviceIds.isEmpty() ? 0 : 1;
        List<String> safeDeviceIds = useDeviceIds == 1 ? deviceIds : List.of("__NO_DEVICE__");

        List<String> cateIdsNorm = (cateIds == null) ? List.of() :
                cateIds.stream().filter(s -> s != null && !s.isBlank()).toList();
        int useCateIds = cateIdsNorm.isEmpty() ? 0 : 1;
        List<String> safeCateIds = useCateIds == 1 ? cateIdsNorm : List.of("__NO_CATE__");

        var rows = historyRepo.latestPerKey(
                facId, scadaId, cate,
                blankToNull(boxDeviceId),

                useDeviceIds, safeDeviceIds,
                useCateIds, safeCateIds
        );

        return rows.stream().map(r -> LatestRecordDto.builder()
                .boxDeviceId(r.getBoxDeviceId())
                .plcAddress(r.getPlcAddress())
                .value(r.getValue())
                .recordedAt(r.getRecordedAt())
                .cateId(r.getCateId())
                .scadaId(r.getScadaId())
                .fac(r.getFac())
                .cate(r.getCate())
                .boxId(r.getBoxId())
                .build()
        ).toList();
    }

    public List<MinutePointView> getSeriesByMinute(
            LocalDateTime fromTs,
            LocalDateTime toTs,
            String boxDeviceId,
            String plcAddress,
            List<String> cateIds
    ) {
        List<String> cateIdsNorm = (cateIds == null) ? List.of()
                : cateIds.stream().filter(s -> s != null && !s.isBlank()).toList();

        int useCateIds = cateIdsNorm.isEmpty() ? 0 : 1;
        List<String> safeCateIds = useCateIds == 1 ? cateIdsNorm : List.of("__NO_CATE__");

        return historyRepo.seriesByMinuteLast( // hoặc Avg
                fromTs, toTs,
                blankToNull(boxDeviceId),
                blankToNull(plcAddress),
                useCateIds, safeCateIds
        );
    }


    // 6) POST /series
    public UtilitySeriesResponse getSeries(UtilitySeriesRequest req) {
        if (req.getFrom() == null || req.getTo() == null) {
            throw new IllegalArgumentException("from/to is required");
        }

        final List<UtilitySeriesRequest.SeriesParamKey> paramsToQuery;

        if (req.getParams() != null && !req.getParams().isEmpty()) {
            paramsToQuery = req.getParams();
        } else {
            List<ChannelDto> chs = getChannels(req.getFacId(), req.getScadaId(), req.getCate());
            Set<String> devs = chs.stream().map(ChannelDto::getBoxDeviceId).collect(Collectors.toSet());
            if (devs.isEmpty()) return UtilitySeriesResponse.builder().series(List.of()).build();

            List<ParamDto> ps = getParams(null, req.getCate(), req.getFacId());

            paramsToQuery = ps.stream()
                    .filter(p -> devs.contains(p.getBoxDeviceId()))
                    .map(p -> UtilitySeriesRequest.SeriesParamKey.builder()
                            .boxDeviceId(p.getBoxDeviceId())
                            .plcAddress(p.getPlcAddress())
                            .build())
                    .toList();
        }

        List<UtilitySeriesResponse.SeriesItem> items = new ArrayList<>();
        for (var k : paramsToQuery) {
            var rows = historyRepo.seriesRaw(k.getBoxDeviceId(), k.getPlcAddress(), req.getFrom(), req.getTo());
            var points = rows.stream()
                    .map(r -> UtilitySeriesResponse.Point.builder().t(r.getRecordedAt()).v(r.getValue()).build())
                    .toList();

            items.add(UtilitySeriesResponse.SeriesItem.builder()
                    .boxDeviceId(k.getBoxDeviceId())
                    .plcAddress(k.getPlcAddress())
                    .points(points)
                    .build());
        }

        return UtilitySeriesResponse.builder().series(items).build();
    }



    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
