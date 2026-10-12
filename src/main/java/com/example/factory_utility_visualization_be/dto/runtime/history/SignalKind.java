package com.example.factory_utility_visualization_be.dto.runtime.history;

public enum SignalKind {
	ANALOG,
	STATUS,
	// Cumulative counter (e.g. energy): points also carry a delta
	COUNTER
}
