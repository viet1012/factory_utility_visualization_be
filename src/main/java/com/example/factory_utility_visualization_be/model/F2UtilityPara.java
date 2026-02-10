package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "f2_utility_para")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class F2UtilityPara {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "box_device_id", length = 100, nullable = false)
    private String boxDeviceId;

    @Column(name = "plc_address", length = 50, nullable = false)
    private String plcAddress;      // D1, D2...

    @Column(name = "value_type", length = 20, nullable = false)
    private String valueType;       // INT/BYTE/FLOAT...

    @Column(name = "unit", length = 20)
    private String unit;            // A, kW...

    @Column(name = "cate_id", length = 100)
    private String cateId;        // Current, Voltage...

    @Column(name = "category", length = 100)
    private String category;        // Current, Voltage...

    @Column(name = "name_vi", length = 255)
    private String nameVi;

    @Column(name = "name_en", length = 255)
    private String nameEn;

    @Column(name = "is_important")
    private Boolean isImportant;

    @Column(name = "is_alert")
    private Boolean isAlert;
}
