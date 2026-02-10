package com.example.factory_utility_visualization_be.response.test;


import com.example.factory_utility_visualization_be.dto.test.FacilityDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class DashboardResponse {
    private LocalDateTime timestamp;
    private List<FacilityDTO> facilities;

}
