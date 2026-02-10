package com.example.factory_utility_visualization_be.dto.test;


import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UtilityLatestDTO {
    private Long id;
    private String plcAddress;
    private Double plcValue;
    private LocalDateTime valueTime;

    private String dataType;
    private String unitName;
    private String position;
    private String description;
    private String fullName;
    private String shortName;
    private String fac;
}
