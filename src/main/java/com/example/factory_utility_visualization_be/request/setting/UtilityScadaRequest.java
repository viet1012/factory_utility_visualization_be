package com.example.factory_utility_visualization_be.request.setting;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtilityScadaRequest {

	private String scadaId;
	private String fac;
	private String plcIp;
	private Integer plcPort;
	private String pcName;
	private String wlan;
	private String connected;
	private Boolean alert;
	private LocalDateTime timeUpdate;

}