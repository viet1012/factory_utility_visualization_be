package com.example.factory_utility_visualization_be.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "F2_Utility_Master_PickData")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class F2UtilityMasterPickData {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "cate_id", length = 100)
	private String cateId;

	@Column(name = "cate_name", length = 255)
	private String cateName;

	@Column(name = "pick_hour")
	private Integer pickHour;
}
