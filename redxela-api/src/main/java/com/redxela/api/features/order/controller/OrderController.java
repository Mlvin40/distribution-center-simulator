package com.redxela.api.features.order.controller;

import com.redxela.api.features.order.dto.CreateOrderRequest;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.order.service.OrderIntakeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Registrar un pedido no inicia trabajo en el hilo HTTP ni presupone inventario disponible
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final OrderIntakeService intake;
    public OrderController(OrderService orders, OrderIntakeService intake) { this.orders = orders; this.intake = intake; }
    @PostMapping public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
        var order = intake.create(request.customerId(), request.lines().stream()
                .map(line -> new OrderSelection(line.productId(), line.quantity())).toList(), OrderSource.MANUAL);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }
    @GetMapping public List<Order> list(@RequestParam(required = false) Integer limit) { return limit == null ? orders.list() : orders.recent(limit); }
    @GetMapping("/{id}") public Order get(@PathVariable UUID id) { return orders.get(id); }
}
