package com.redxela.api.features.storage.model;

import java.util.List;

// Geometría inmutable; las cantidades y asignaciones por lote se consultan en StorageOverview
public record StorageLayout(long totalCapacity, int locationCapacity, List<Location> locations) {
    public StorageLayout { locations = List.copyOf(locations); }
    public record Location(String id, int aisle, int level, int position, int capacity) {}
}
