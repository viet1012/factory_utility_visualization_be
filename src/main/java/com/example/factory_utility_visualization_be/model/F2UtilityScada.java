package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "f2_utility_scada")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class F2UtilityScada {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scada_id")
	private String scadaId;

	@Column(name = "fac")
	private String fac;

	@Column(name = "plc_ip")
	private String plcIp;

	@Column(name = "plc_port")
	private Integer plcPort;

	@Column(name = "pc_name")
	private String pcName;

	@Column(name = "wlan")
	private String wlan;

	@Column(name = "connected")
	private String connected;

	@Column(name = "alert")
	private Boolean alert;

	@Column(name = "time_update")
	private LocalDateTime timeUpdate;

}
