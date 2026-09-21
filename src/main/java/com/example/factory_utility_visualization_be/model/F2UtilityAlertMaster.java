package com.example.factory_utility_visualization_be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "F2_Utility_Alert_Master", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class F2UtilityAlertMaster {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "Id")
	private Long id;

	@Column(name = "Device")
	private String device;

	@Column(name = "RuleName")
	private String ruleName;

	@Column(name = "Unit")
	private String unit;

	@Column(name = "MinValue")
	private BigDecimal minValue;

	@Column(name = "MaxValue")
	private BigDecimal maxValue;

	@Column(name = "AlertLevel")
	private String alertLevel;

	@Column(name = "AlertDescription")
	private String alertDescription;

	@Column(name = "IsActive")
	private Boolean isActive;

	@Column(name = "CreatedBy")
	private String createdBy;

	@Column(name = "CreatedAt")
	private LocalDateTime createdAt;

	@Column(name = "UpdatedBy")
	private String updatedBy;

	@Column(name = "UpdatedAt")
	private LocalDateTime updatedAt;

	@Column(name = "RuleType")
	private String ruleType;

	@Column(name = "CompareOperator")
	private String compareOperator;

	@Column(name = "WindowMinutes")
	private Integer windowMinutes;

	@Column(name = "AggregateType")
	private String aggregateType;

	@Column(name = "ThresholdValue")
	private BigDecimal thresholdValue;

	@Column(name = "ParameterCode")
	private String parameterCode;
}
