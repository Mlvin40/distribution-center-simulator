package com.redxela.api.features.inventory.controller;

import com.redxela.api.features.inventory.model.*;
import com.redxela.api.features.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

// Compatibilidad de ruta: toda entrada HTTP pasa por Recepción y su cola asíncrona
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventory;
    private final com.redxela.api.features.reception.service.ReceptionService reception;
    public InventoryController(InventoryService inventory, com.redxela.api.features.reception.service.ReceptionService reception) { this.inventory = inventory; this.reception = reception; }
    @GetMapping public InventoryOverview snapshot() { return inventory.snapshot(); }
    @PostMapping("/receipts") public com.redxela.api.features.reception.model.Arrival receive(@Valid @RequestBody ReceiptRequest request) {
        return reception.register(request.arrivalId(), request.productId(), request.quantity());
    }
    @GetMapping("/reservations/{orderId}") public InventoryReservation reservation(@PathVariable UUID orderId) { return inventory.reservation(orderId); }
    public record ReceiptRequest(@NotNull UUID arrivalId, @NotBlank String productId, @NotNull @Positive Long quantity) {}
}
