package com.example.factory_utility_visualization_be.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LatestRecordDto {
    private String boxDeviceId;
    private String plcAddress;
    private BigDecimal value;
    private LocalDateTime recordedAt;

    private String cateId;
    private String scadaId;
    private String fac;
    private String cate;
    private String boxId;
    private String name_en;
    private String unit;

    // Alarm fields
    private Double minVol;
    private Double maxVol;
    private Double minVolStd;
    private Double maxVolStd;
    private String alarm; // Alarm / Normal
}