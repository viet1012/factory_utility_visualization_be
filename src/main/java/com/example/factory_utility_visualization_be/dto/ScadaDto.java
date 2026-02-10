package com.example.factory_utility_visualization_be.dto;


import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScadaDto {
    private String scadaId;
    private String fac;
    private String plcIp;
    private Integer plcPort;
    private String wlan;
}
