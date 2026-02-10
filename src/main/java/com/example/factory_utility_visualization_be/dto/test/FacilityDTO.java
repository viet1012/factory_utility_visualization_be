package com.example.factory_utility_visualization_be.dto.test;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class FacilityDTO {
    private String fac;
    private LocalDateTime lastUpdate;
    private List<SignalDTO> signals;


}
