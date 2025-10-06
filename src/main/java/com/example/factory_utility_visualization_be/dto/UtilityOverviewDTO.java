package com.example.factory_utility_visualization_be.dto;


import java.time.LocalDateTime;

public class UtilityOverviewDTO {
    private String plcAddress;
    private String plcValue;
    private String position;
    private String comment;
    private LocalDateTime dataTime;

    public UtilityOverviewDTO(String plcAddress, String plcValue, String position, String comment, LocalDateTime dataTime) {
        this.plcAddress = plcAddress;
        this.plcValue = plcValue;
        this.position = position;
        this.comment = comment;
        this.dataTime = dataTime;
    }

    // Getters và Setters
    public String getPlcAddress() { return plcAddress; }
    public void setPlcAddress(String plcAddress) { this.plcAddress = plcAddress; }

    public String getPlcValue() { return plcValue; }
    public void setPlcValue(String plcValue) { this.plcValue = plcValue; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public LocalDateTime getDataTime() { return dataTime; }
    public void setDataTime(LocalDateTime dataTime) { this.dataTime = dataTime; }
}
