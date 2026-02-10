package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "f2_utility_scada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class F2UtilityScada {

	@Id
	@Column(name = "scada_id", length = 50)
	private String scadaId;     // A1, A2, B1...

	@Column(name = "fac", length = 50, nullable = false)
	private String fac;         // Fac_A, Fac_B...

	@Column(name = "plc_ip", length = 50, nullable = false)
	private String plcIp;

	@Column(name = "plc_port", nullable = false)
	private Integer plcPort;

	@Column(name = "wlan", length = 50)
	private String wlan;
}
