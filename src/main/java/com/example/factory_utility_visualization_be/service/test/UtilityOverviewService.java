package com.example.factory_utility_visualization_be.service.test;

import com.example.factory_utility_visualization_be.dto.test.FacilityDTO;
import com.example.factory_utility_visualization_be.dto.test.SignalDTO;
import com.example.factory_utility_visualization_be.repository.test.UtilityOverviewRepository;
import com.example.factory_utility_visualization_be.response.test.DashboardResponse;
import org.springframework.stereotype.Service;

import java.util.List;


import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.*;

@Service
public class UtilityOverviewService {

    private final UtilityOverviewRepository repository;

    public UtilityOverviewService(UtilityOverviewRepository repository) {
        this.repository = repository;
    }


//    public DashboardResponse getOverview(List<String> facList) {
//
//        // normalize: null / empty / trim
//        if (facList != null) {
//            facList = facList.stream()
//                    .filter(s -> s != null && !s.trim().isEmpty())
//                    .map(String::trim)
//                    .distinct()
//                    .toList();
//            if (facList.isEmpty()) facList = null;
//        }
//
//        List<Object[]> rows = repository.findLatestUtilityDataByFact(facList);
//
//        Map<String, List<SignalDTO>> facToSignals = new LinkedHashMap<>();
//
//        for (Object[] row : rows) {
//            // theo thứ tự SELECT ở repo
//            String plcAddress = (String) row[1];
//            Object plcValueObj = row[2];
//            String plcValue = plcValueObj == null ? null : plcValueObj.toString();
//
//            LocalDateTime dateadd = null;
//            if (row[3] instanceof Timestamp ts) dateadd = ts.toLocalDateTime();
//
//            String dataType = (String) row[4];
//            String unit = (String) row[5];
//            String position = (String) row[6];
//            String description = (String) row[7];
//            String fullName = (String) row[8];
//            String shortName = (String) row[9];
//            String fac = (String) row[10];
//
//            SignalDTO signal = new SignalDTO(
//                    plcAddress,
//                    description,
//                    fullName,
//                    shortName,
//                    plcValue,
//                    unit,
//                    dataType,
//                    position,
//                    dateadd
//            );
//
//            facToSignals.computeIfAbsent(fac, k -> new ArrayList<>()).add(signal);
//        }
//
//        List<FacilityDTO> facilities = facToSignals.entrySet().stream()
//                .map(entry -> {
//                    String fac = entry.getKey();
//                    List<SignalDTO> signals = entry.getValue();
//
//                    LocalDateTime lastUpdate = signals.stream()
//                            .map(SignalDTO::getDateadd)
//                            .filter(Objects::nonNull)
//                            .max(LocalDateTime::compareTo)
//                            .orElse(null);
//
//                    return new FacilityDTO(fac, lastUpdate, signals);
//                })
//                .toList();
//
//        return new DashboardResponse(LocalDateTime.now(), facilities);
//    }


    private List<String> normalizeFacList(List<String> facList) {
        if (facList == null) return null;
        var out = facList.stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
        return out.isEmpty() ? null : out;
    }

    private DashboardResponse mapRowsToResponse(List<Object[]> rows) {
        Map<String, List<SignalDTO>> facToSignals = new LinkedHashMap<>();

        for (Object[] row : rows) {
            String plcAddress = (String) row[1];
            Object plcValueObj = row[2];
            String plcValue = plcValueObj == null ? null : plcValueObj.toString();

            LocalDateTime dateadd = null;
            if (row[3] instanceof Timestamp ts) dateadd = ts.toLocalDateTime();

            String dataType = (String) row[4];
            String unit = (String) row[5];
            String position = (String) row[6];
            String description = (String) row[7];
            String fullName = (String) row[8];
            String shortName = (String) row[9];
            String fac = (String) row[10];

            SignalDTO signal = new SignalDTO(
                    plcAddress,
                    description,
                    fullName,
                    shortName,
                    plcValue,
                    unit,
                    dataType,
                    position,
                    dateadd
            );

            facToSignals.computeIfAbsent(fac, k -> new ArrayList<>()).add(signal);
        }

        List<FacilityDTO> facilities = facToSignals.entrySet().stream()
                .map(entry -> {
                    String fac = entry.getKey();
                    List<SignalDTO> signals = entry.getValue();

                    LocalDateTime lastUpdate = signals.stream()
                            .map(SignalDTO::getDateadd)
                            .filter(Objects::nonNull)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);

                    return new FacilityDTO(fac, lastUpdate, signals);
                })
                .toList();

        return new DashboardResponse(LocalDateTime.now(), facilities);
    }

    public DashboardResponse getOverview(List<String> facList) {
        facList = normalizeFacList(facList);

        List<Object[]> rows = repository.findLatestUtilityDataByFact(facList);

        return mapRowsToResponse(rows);
    }

    public DashboardResponse getOverviewInRange(List<String> facList,
                                                LocalDateTime from,
                                                LocalDateTime to) {

        facList = normalizeFacList(facList);

        // guard
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to must not be null");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }

        List<Object[]> rows = repository.getOverviewInRange(
                facList,
                Timestamp.valueOf(from),
                Timestamp.valueOf(to)
        );

        return mapRowsToResponse(rows);
    }

}
