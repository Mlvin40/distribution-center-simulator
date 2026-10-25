package com.redxela.api.features.reception.controller;

import com.redxela.api.features.inventory.controller.InventoryController.ReceiptRequest;
import com.redxela.api.features.reception.model.Arrival;
import com.redxela.api.features.reception.service.ReceptionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

// Acepta comandos breves; escaneo, mensajes y almacenamiento se ejecutan fuera de HTTP
@RestController
@RequestMapping("/api/reception")
public class ReceptionController {
    private final ReceptionService reception;
    public ReceptionController(ReceptionService reception) { this.reception=reception; }
    @GetMapping public ReceptionService.Overview snapshot() { return reception.snapshot(); }
    @GetMapping("/{id}") public Arrival get(@PathVariable UUID id) { return reception.get(id); }
    @PostMapping("/arrivals") public Arrival register(@Valid @RequestBody ReceiptRequest request) { return reception.register(request.arrivalId(),request.productId(),request.quantity()); }
}
