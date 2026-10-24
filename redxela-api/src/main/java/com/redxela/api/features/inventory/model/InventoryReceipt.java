package com.redxela.api.features.inventory.model;

import java.time.Instant;
import java.util.*;

public record InventoryReceipt(UUID arrivalId, String productId, long quantity, Instant receivedAt,
                               double receivedSimulatedSeconds, List<UUID> reactivatedOrders) {
    public InventoryReceipt { reactivatedOrders = List.copyOf(reactivatedOrders); }
}
