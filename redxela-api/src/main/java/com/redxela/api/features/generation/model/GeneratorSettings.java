package com.redxela.api.features.generation.model;

import jakarta.validation.constraints.*;

public record GeneratorSettings(@DecimalMin("0.1") @DecimalMax("3600") double intervalSimulatedSeconds,
                                long seed, @Min(1) @Max(100) int minUnits, @Min(1) @Max(100) int maxUnits) {
    public GeneratorSettings {
        if (!Double.isFinite(intervalSimulatedSeconds) || intervalSimulatedSeconds < .1 || intervalSimulatedSeconds > 3600
                || minUnits < 1 || maxUnits > 100 || minUnits > maxUnits)
            throw new IllegalArgumentException("Intervalo entre 0.1 y 3600 segundos simulados y cantidades entre 1 y 100 en orden ascendente");
    }
}
