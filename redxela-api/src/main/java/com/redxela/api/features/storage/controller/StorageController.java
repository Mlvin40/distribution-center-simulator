package com.redxela.api.features.storage.controller;

import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.storage.model.StorageOverview;
import org.springframework.web.bind.annotation.*;

// Consulta el mismo snapshot transaccional que utiliza Inventario
@RestController
@RequestMapping("/api/storage")
public class StorageController {
    private final InventoryService inventory;
    public StorageController(InventoryService inventory) { this.inventory = inventory; }
    @GetMapping public StorageOverview snapshot() { return inventory.storageSnapshot(); }
}
