package com.redxela.api.features.simulation.service;

import com.redxela.api.features.simulation.config.SimulationProperties;
import java.time.Instant;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// El reloj monotónico evita retrocesos por ajustes del reloj civil del sistema
@Component
public class SimulationClock {
    private final double scale;
    private final LongSupplier nanoTime;
    private final long origin;
    private final Instant realOrigin;

    @Autowired
    public SimulationClock(SimulationProperties properties) {
        this(properties.timeScale(), System::nanoTime, Instant.now());
    }
    public SimulationClock(double scale, LongSupplier nanoTime, Instant realOrigin) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Escala inválida");
        this.scale = scale;
        this.nanoTime = nanoTime;
        this.origin = nanoTime.getAsLong();
        this.realOrigin = realOrigin;
    }
    public double elapsedSeconds() { return (nanoTime.getAsLong() - origin) / 1_000_000_000.0 * scale; }
    public Instant realOrigin() { return realOrigin; }
}
