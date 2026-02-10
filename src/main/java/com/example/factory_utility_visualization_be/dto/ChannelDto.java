package com.example.factory_utility_visualization_be.dto;


import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChannelDto {
    private Long id;
    private String scadaId;
    private String cate;
    private String boxDeviceId;
    private String boxId;
}
