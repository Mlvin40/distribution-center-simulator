package com.redxela.api.features.storage.model;

import java.util.*;

// Reservar conserva las unidades físicas hasta el despacho
public record StorageOverview(long capacity, long occupied, long reserved, long availableCapacity,
                              List<LocationStock> locations) {
    public StorageOverview { locations = List.copyOf(locations); }
    public record LotStock(UUID lotId, String productId, long quantity, long reserved) {}
    public record LocationStock(StorageLayout.Location location, long occupied, long reserved,
                                long availableCapacity, List<LotStock> lots) {
        public LocationStock { lots = List.copyOf(lots); }
    }
}
