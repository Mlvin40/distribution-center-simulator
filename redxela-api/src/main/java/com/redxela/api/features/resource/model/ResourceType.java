package com.redxela.api.features.resource.model;

// Las capacidades pertenecen al centro y no dependen del nivel de servicio
public enum ResourceType {
    PACKING_STATION(6), LOADING_AREA(2), WAREHOUSE_WORKER(10),
    FORKLIFT(3), QUALITY_STATION(4), SCANNER(1);

    private final int capacity;
    ResourceType(int capacity) { this.capacity = capacity; }
    public int capacity() { return capacity; }
}
