package com.example.factory_utility_visualization_be.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "F2_Utility_Overlay_Group_Pos")
@Getter
@Setter
public class UtilityOverlayPos {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "fac_id", nullable = false)
	private String facId;

	@Column(name = "box_device_id", nullable = false)
	private String boxDeviceId;

	@Column(nullable = false)
	private Double x;

	@Column(nullable = false)
	private Double y;

	@Column(name = "direction")
	private String direction;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;
}