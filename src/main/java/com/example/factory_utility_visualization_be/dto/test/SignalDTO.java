package com.example.factory_utility_visualization_be.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
public class SignalDTO {
    private String plcAddress;
    private String description;
    private String fullName;
    private String shortName;
    private String value;
    private String unit;
    private String dataType;
    private String position;
    private LocalDateTime dateadd;


}
