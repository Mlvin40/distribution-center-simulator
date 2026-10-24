package com.redxela.api.features.order.service;

import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.order.model.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.springframework.stereotype.Service;

// HTTP y generador comparten la misma admisión; publicar el pedido y reservar stock es una transacción breve
@Service
public class OrderIntakeService {
    private final OrderService orders;
    private final InventoryService inventory;
    public OrderIntakeService(OrderService orders, InventoryService inventory) { this.orders = orders; this.inventory = inventory; }
    public Order create(UUID customerId, List<OrderSelection> selections, OrderSource source) {
        return orders.atomically(() -> {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Admisión interrumpida");
            var order = orders.create(customerId, selections, source);
            // La reserva termina la admisión incluso si llega una interrupción después de publicar el pedido
            inventory.reconsiderAdmitted(order.id());
            return orders.get(order.id());
        });
    }
}
