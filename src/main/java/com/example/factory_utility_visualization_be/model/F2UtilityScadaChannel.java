package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "f2_utility_scada_channel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class F2UtilityScadaChannel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "scada_id", length = 50, nullable = false)
	private String scadaId;

	@Column(name = "cate", length = 50, nullable = false)
	private String cate;              // Electricity...

	@Column(name = "box_device_id", length = 100, nullable = false)
	private String boxDeviceId;       // DB_P1_400A...

	@Column(name = "box_id", length = 100, nullable = false)
	private String boxId;             // DB_P1...
}

