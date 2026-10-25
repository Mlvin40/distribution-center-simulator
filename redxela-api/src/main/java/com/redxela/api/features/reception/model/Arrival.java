package com.redxela.api.features.reception.model;

import com.redxela.api.features.inventory.model.InventoryReceipt;
import java.time.Instant;
import java.util.UUID;

// Mensaje inmutable: Recepción solo publica identidad y contenido; Inventario confirma su aplicación
public record Arrival(UUID arrivalId, String productId, long quantity, Instant registeredAt,
                      Status status, int attempts, String message, InventoryReceipt receipt) {
    public enum Status { RECEIVED, SCANNING, QUEUED, PROCESSING, WAITING_CAPACITY, RETRYING, COMPLETED }
    public Arrival update(Status next, int count, String detail, InventoryReceipt result) {
        return new Arrival(arrivalId,productId,quantity,registeredAt,next,count,detail,result);
    }
}
