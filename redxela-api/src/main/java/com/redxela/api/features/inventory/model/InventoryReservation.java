package com.redxela.api.features.inventory.model;

import java.time.Instant;
import java.util.*;

// La reserva pertenece al pedido, no a un hilo; su historial impide consumir o liberar dos veces
public record InventoryReservation(UUID orderId, Map<String, Long> quantities, Status status, Instant createdAt) {
    public enum Status { RESERVED, CONSUMED, RELEASED }
    public InventoryReservation { quantities = Map.copyOf(quantities); }
    public InventoryReservation withStatus(Status next) { return new InventoryReservation(orderId, quantities, next, createdAt); }
}
