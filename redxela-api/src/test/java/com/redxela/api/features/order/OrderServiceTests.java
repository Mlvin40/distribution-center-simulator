package com.redxela.api.features.order;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.ConflictException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTests {
    private final CustomerService customers = new CustomerService();
    private final SimulationProperties settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
    private final OrderService orders = new OrderService(customers, new ProductCatalog(), new SimulationClock(settings), settings);
    @Test void concurrentCreationPreservesAllCustomersOrdersAndUniqueIds() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<UUID>> tasks = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                int level = i % 5 + 1;
                tasks.add(() -> {
                    var customer = customers.create("Concurrente", level);
                    var order = orders.create(customer.id(), List.of(new OrderSelection("PRD-001", 2), new OrderSelection("PRD-005", 3)));
                    assertEquals(customer.serviceLevel(), order.serviceLevel());
                    assertEquals(5, order.totalUnits());
                    assertEquals(order, orders.get(order.id()));
                    return order.id();
                });
            }
            var ids = new HashSet<UUID>();
            for (var future : pool.invokeAll(tasks, 10, TimeUnit.SECONDS)) ids.add(future.get());
            assertEquals(500, ids.size());
            assertEquals(500, customers.list().size());
            assertEquals(500, orders.list().size());
        }
    }
    @Test void immutableSnapshotsAtomicTransitionsAndRelevantTimes() throws Exception {
        var customer = customers.create("Ana", 1);
        var initial = orders.create(customer.id(), List.of(new OrderSelection("PRD-001", 1)));
        assertThrows(UnsupportedOperationException.class, () -> initial.lines().clear());
        assertThrows(UnsupportedOperationException.class, () -> initial.history().clear());
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 50; i++) tasks.add(() -> {
                try { orders.transition(initial.id(), OrderStatus.WAITING_INVENTORY, OrderStatus.READY); return true; }
                catch (ConflictException ex) { return false; }
            });
            int successes = 0;
            for (var future : pool.invokeAll(tasks, 5, TimeUnit.SECONDS)) if (future.get()) successes++;
            assertEquals(1, successes);
        }
        assertEquals(OrderStatus.WAITING_INVENTORY, initial.status());
        assertEquals(2, initial.history().size());
        var previous = OrderStatus.READY;
        for (var next : List.of(OrderStatus.WAITING_RESOURCES, OrderStatus.PICKING, OrderStatus.PACKING,
                OrderStatus.SCANNING, OrderStatus.LOADING, OrderStatus.COMPLETED)) {
            orders.transition(initial.id(), previous, next); previous = next;
        }
        var completed = orders.get(initial.id());
        assertNotNull(completed.processingStartedAt());
        assertNotNull(completed.completedAt());
        assertFalse(completed.completedAt().isBefore(completed.processingStartedAt()));
        assertTrue(completed.history().getLast().simulatedSeconds() >= completed.history().getFirst().simulatedSeconds());
        assertThrows(ConflictException.class, () -> orders.transition(initial.id(), OrderStatus.COMPLETED, OrderStatus.READY));
    }
}
