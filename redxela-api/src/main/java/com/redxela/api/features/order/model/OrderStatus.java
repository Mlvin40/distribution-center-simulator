package com.redxela.api.features.order.model;

import java.util.Set;

// Solo se admiten transiciones explícitas; los estados terminales no se reabren
public enum OrderStatus {
    RECEIVED, WAITING_INVENTORY, READY, WAITING_RESOURCES, PICKING,
    QUALITY_CHECK, PACKING, SCANNING, LOADING, COMPLETED, CANCELLED, FAILED;

    public boolean canTransitionTo(OrderStatus next) {
        if (Set.of(COMPLETED, CANCELLED, FAILED).contains(this)) return false;
        if (next == CANCELLED || next == FAILED) return true;
        return switch (this) {
            case RECEIVED -> next == WAITING_INVENTORY;
            case WAITING_INVENTORY -> next == READY;
            case READY -> next == WAITING_RESOURCES;
            case WAITING_RESOURCES -> next == PICKING || next == READY;
            case PICKING -> next == QUALITY_CHECK || next == PACKING;
            case QUALITY_CHECK -> next == PACKING;
            case PACKING -> next == SCANNING;
            case SCANNING -> next == LOADING;
            case LOADING -> next == COMPLETED;
            default -> false;
        };
    }
}
