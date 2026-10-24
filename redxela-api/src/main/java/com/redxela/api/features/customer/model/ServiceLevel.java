package com.redxela.api.features.customer.model;

// Exprés usa cero como objetivo de atención inmediata, sin prometer una garantía imposible
public enum ServiceLevel {
    EXPRESS(1, "Exprés", 0), PRIORITY(2, "Prioritario", 10), STANDARD(3, "Estándar", 30),
    SCHEDULED(4, "Programado", 60), ECONOMY(5, "Económico", 120);
    private final int level;
    private final String displayName;
    private final int targetWaitMinutes;
    ServiceLevel(int level, String displayName, int targetWaitMinutes) {
        this.level = level; this.displayName = displayName; this.targetWaitMinutes = targetWaitMinutes;
    }
    public int level() { return level; }
    public String displayName() { return displayName; }
    public int targetWaitMinutes() { return targetWaitMinutes; }
    public static ServiceLevel fromLevel(int level) {
        for (var value : values()) if (value.level == level) return value;
        throw new IllegalArgumentException("El nivel de servicio debe estar entre 1 y 5");
    }
}
