package com.example.factory_utility_visualization_be.service;

import com.example.factory_utility_visualization_be.dto.UtilityOverviewDTO;
import com.example.factory_utility_visualization_be.repository.UtilityDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UtilityOverviewService {

    @Autowired
    private UtilityDataRepository repository;


    public List<UtilityOverviewDTO> getLatestData() {
        List<Object[]> results = repository.findLatestUtilityData();
        return results.stream().map(row -> new UtilityOverviewDTO(
                (String) row[0],
                row[1] != null ? row[1].toString() : null,
                (String) row[2],
                (String) row[3],
                row[4] != null ? ((java.sql.Timestamp) row[4]).toLocalDateTime() : null
        )).collect(Collectors.toList());
    }
}
