package com.redxela.api.features.inventory.model;

import com.redxela.api.features.product.model.Product;
import java.time.Instant;
import java.util.*;

// OnHand incluye lo reservado: reservar no libera capacidad física del almacén
public record InventoryOverview(Instant capturedAt, long capacity, long onHand, long reserved,
                                long available, List<Stock> products, List<WaitingOrder> waitingOrders) {
    public InventoryOverview { products = List.copyOf(products); waitingOrders = List.copyOf(waitingOrders); }
    public record Stock(Product product, long onHand, long reserved, long available) {}
    public record Shortage(String productId, long requested, long available, long missing) {}
    public record WaitingOrder(UUID orderId, List<Shortage> shortages) {
        public WaitingOrder { shortages = List.copyOf(shortages); }
    }
}
