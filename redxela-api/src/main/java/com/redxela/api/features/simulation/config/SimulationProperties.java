package com.redxela.api.features.simulation.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// La capacidad se deriva de una geometría explícita y el tiempo siempre se expresa en segundos
@Validated
@ConfigurationProperties("redxela.simulation")
public record SimulationProperties(
        @DefaultValue("60") @DecimalMin("0.01") @DecimalMax("3600") double timeScale,
        @DefaultValue("10") @Min(1) @Max(3600) int workSecondsPerUnit,
        @DefaultValue("3") @Min(1) @Max(100) int aisles,
        @DefaultValue("2") @Min(1) @Max(100) int levels,
        @DefaultValue("5") @Min(1) @Max(100) int positionsPerLevel,
        @DefaultValue("20") @Min(1) @Max(1000) int locationCapacity) {
    public long storageCapacity() {
        return (long) aisles * levels * positionsPerLevel * locationCapacity;
    }
    public long baseWorkSeconds(long totalUnits) {
        if (totalUnits <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva");
        return Math.multiplyExact(totalUnits, workSecondsPerUnit);
    }
}
