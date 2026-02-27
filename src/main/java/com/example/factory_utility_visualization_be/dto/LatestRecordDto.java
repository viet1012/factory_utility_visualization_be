package com.example.factory_utility_visualization_be.dto;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatestRecordDto {
    private String boxDeviceId;
    private String plcAddress;
    private BigDecimal value;
    private LocalDateTime recordedAt;

    // enrich (optional)
    private String cateId;
    private String scadaId;
    private String fac;
    private String cate;
    private String boxId;
    private String name_en;

}
