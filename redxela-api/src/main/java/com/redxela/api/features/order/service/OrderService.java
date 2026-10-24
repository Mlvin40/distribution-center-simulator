package com.redxela.api.features.order.service;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.ResourceType;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

// El monitor coordina transacciones entre pedidos, planificación e inventario; los valores siguen siendo inmutables
@Service
public class OrderService {
    private final ConcurrentHashMap<UUID, Order> orders = new ConcurrentHashMap<>();
    private final CustomerService customers;
    private final ProductCatalog catalog;
    private final SimulationClock clock;
    private final SimulationProperties properties;
    public OrderService(CustomerService customers, ProductCatalog catalog, SimulationClock clock, SimulationProperties properties) {
        this.customers = customers; this.catalog = catalog; this.clock = clock; this.properties = properties;
    }
    public synchronized Order create(UUID customerId, List<OrderSelection> selections) {
        return create(customerId, selections, OrderSource.MANUAL);
    }
    public synchronized Order create(UUID customerId, List<OrderSelection> selections, OrderSource source) {
        Objects.requireNonNull(source);
        if (customerId == null || selections == null || selections.isEmpty())
            throw new IllegalArgumentException("Cliente y al menos una línea son obligatorios");
        var customer = customers.get(customerId);
        var lines = new ArrayList<OrderLine>();
        var seen = new HashSet<String>();
        var resources = EnumSet.noneOf(ResourceType.class);
        long totalUnits = 0;
        for (var selection : List.copyOf(selections)) {
            if (!seen.add(selection.productId())) throw new IllegalArgumentException("No se permite repetir un producto en varias líneas");
            var product = catalog.get(selection.productId());
            lines.add(new OrderLine(product, selection.quantity()));
            totalUnits = Math.addExact(totalUnits, selection.quantity());
            resources.addAll(product.merchandiseType().requiredResources());
        }
        var now = Instant.now();
        var simulated = clock.elapsedSeconds();
        Order order;
        do {
            order = new Order(UUID.randomUUID(), source, customer.id(), customer.serviceLevel(), lines, totalUnits,
                    resources, properties.baseWorkSeconds(totalUnits), OrderStatus.RECEIVED, now, null, null,
                    List.of(new OrderTransition(OrderStatus.RECEIVED, now, simulated)))
                    .transitionTo(OrderStatus.WAITING_INVENTORY, now, simulated);
        } while (orders.putIfAbsent(order.id(), order) != null);
        return order;
    }
    public synchronized Order get(UUID id) {
        var order = orders.get(id);
        if (order == null) throw new NotFoundException("Pedido no encontrado");
        return order;
    }
    public synchronized List<Order> list() {
        return orders.values().stream().sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::id)).toList();
    }
    public synchronized long count() { return orders.mappingCount(); }
    public synchronized List<Order> recent(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("El límite debe estar entre 1 y 500");
        return orders.values().stream().sorted(Comparator.comparing(Order::createdAt).reversed()
                .thenComparing(Order::id)).limit(limit).toList();
    }
    // Toda operación compuesta usa este mismo monitor y nunca espera recursos mientras lo retiene
    public synchronized <T> T atomically(Supplier<T> operation) { return operation.get(); }
    // Solo el motor avanza pedidos tras verificar stock y recursos, nunca un controlador
    public synchronized Order transition(UUID id, OrderStatus expected, OrderStatus next) {
        return orders.compute(id, (key, current) -> {
            if (current == null) throw new NotFoundException("Pedido no encontrado");
            if (current.status() != expected) throw new ConflictException("El pedido cambió de estado");
            return current.transitionTo(next, Instant.now(), clock.elapsedSeconds());
        });
    }
}
