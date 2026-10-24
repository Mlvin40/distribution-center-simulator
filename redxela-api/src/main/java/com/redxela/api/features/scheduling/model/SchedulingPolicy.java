package com.redxela.api.features.scheduling.model;

import java.util.Comparator;

// El envejecimiento se recalcula con un único instante por decisión; no se usa un heap con claves obsoletas
public enum SchedulingPolicy {
    STRICT_PRIORITY("Prioridad estricta", "Nivel contratado ascendente"),
    AGING("Envejecimiento", "Un nivel de mejora por cada 300 segundos simulados de espera, hasta nivel 1"),
    SHORTEST_JOB_FIRST("Trabajo más corto primero", "Total de unidades ascendente");
    private final String displayName;
    private final String criterion;
    SchedulingPolicy(String displayName, String criterion) { this.displayName = displayName; this.criterion = criterion; }
    public String displayName() { return displayName; }
    public String criterion() { return criterion; }
    public int effectivePriority(SchedulingCandidate candidate, double now) {
        return this == AGING ? (int) Math.max(1, candidate.serviceLevel().level() - Math.floor(candidate.waitSeconds(now) / 300))
                : candidate.serviceLevel().level();
    }
    public Comparator<SchedulingCandidate> comparator(double now) {
        Comparator<SchedulingCandidate> order = this == SHORTEST_JOB_FIRST
                ? Comparator.comparingLong(SchedulingCandidate::totalUnits)
                : Comparator.comparingInt(candidate -> effectivePriority(candidate, now));
        return order.thenComparingDouble(SchedulingCandidate::arrivalSimulatedSeconds).thenComparing(SchedulingCandidate::id);
    }
}
