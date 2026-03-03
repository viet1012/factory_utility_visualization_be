package com.example.factory_utility_visualization_be.dto.overview.daily;


import java.time.LocalDate;
import java.math.BigDecimal;
public record DailyDto(LocalDate date, BigDecimal value) {}