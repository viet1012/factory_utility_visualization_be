package com.example.factory_utility_visualization_be.response.setting;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtilityScadaResponse {

	private Long id;
	private String scadaId;
	private String fac;
	private String plcIp;
	private Integer plcPort;
	private String pcName;
	private String wlan;
	private Boolean connected;
	private Boolean alert;
	private LocalDateTime timeUpdate;

}