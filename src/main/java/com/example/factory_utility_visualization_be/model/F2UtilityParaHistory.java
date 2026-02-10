package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "f2_utility_para_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class F2UtilityParaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "box_device_id", length = 100, nullable = false)
    private String boxDeviceId;

    @Column(name = "plc_address", length = 50, nullable = false)
    private String plcAddress;

    @Column(name = "value", precision = 18, scale = 6)
    private BigDecimal value;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
