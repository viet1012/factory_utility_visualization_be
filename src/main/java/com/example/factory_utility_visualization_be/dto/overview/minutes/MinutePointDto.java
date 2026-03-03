package com.example.factory_utility_visualization_be.dto.overview.minutes;


import java.time.LocalDateTime;

public record MinutePointDto(LocalDateTime ts, Double value) {}