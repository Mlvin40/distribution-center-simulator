package com.redxela.api.features.scheduling;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.scheduling.service.OrderScheduler;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class OrderSchedulerTests {
    private final CustomerService customers = new CustomerService();
    private final AtomicLong nanos = new AtomicLong();
    private final SimulationClock clock = new SimulationClock(1, nanos::get, Instant.EPOCH);
    private final OrderService orders = new OrderService(customers, new ProductCatalog(), clock, new SimulationProperties(1, 10, 3, 2, 5, 20));
    private final ResourceManager resources = new ResourceManager();
    private final OrderScheduler scheduler = new OrderScheduler(orders, resources, clock);
    private Order order(int level, String product, int units, boolean ready) {
        var result = orders.create(customers.create("Planificación", level).id(), List.of(new OrderSelection(product, units)));
        return ready ? orders.transition(result.id(), OrderStatus.WAITING_INVENTORY, OrderStatus.READY) : result;
    }
    @Test void selectionUsesPolicyAndInventoryWaitIsExcluded() {
        var blocked = order(1, "PRD-001", 1, false);
        var large = order(2, "PRD-001", 20, true);
        var small = order(3, "PRD-001", 1, true);
        assertEquals(2, scheduler.snapshot().eligible().size());
        assertEquals(blocked.id(), scheduler.snapshot().waitingInventory().getFirst().order().id());
        try (var lease = scheduler.claimNext().orElseThrow()) { assertEquals(large.id(), lease.orderId()); }
        scheduler.changePolicy(SchedulingPolicy.SHORTEST_JOB_FIRST);
        try (var lease = scheduler.claimNext().orElseThrow()) { assertEquals(small.id(), lease.orderId()); }
        assertEquals(2, scheduler.snapshot().eligible().size());
        assertTrue(scheduler.snapshot().metrics().stream().allMatch(metrics -> metrics.completedOrders() == 0 && metrics.averageProcessingSimulatedSeconds() == null));
    }
    @Test void concurrentSelectionsAndPolicyChangesDoNotDuplicateOrLoseOrders() throws Exception {
        for (int i = 0; i < 30; i++) order(i % 5 + 1, "PRD-001", i + 1, true);
        var selected = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var holder = pool.submit(() -> {
                try (var lease = scheduler.claimNext().orElseThrow()) { selected.countDown(); release.await(5, TimeUnit.SECONDS); }
                return null;
            });
            try {
                assertTrue(selected.await(2, TimeUnit.SECONDS));
                var tasks = new ArrayList<Callable<Void>>();
                for (int i = 0; i < 100; i++) {
                    var policy = SchedulingPolicy.values()[i % 3];
                    tasks.add(() -> { scheduler.changePolicy(policy); assertTrue(scheduler.claimNext().isEmpty()); return null; });
                }
                for (var task : pool.invokeAll(tasks)) task.get();
                assertEquals(1, scheduler.snapshot().claimed().size());
                assertEquals(29, scheduler.snapshot().eligible().size());
                assertEquals(30, orders.count());
            } finally { release.countDown(); }
            holder.get(2, TimeUnit.SECONDS);
        }
        assertEquals(30, scheduler.snapshot().eligible().size());
        assertTrue(scheduler.snapshot().claimed().isEmpty());
        assertEquals(1, resources.snapshot().resources().get(5).available());
    }
    @Test void unavailableResourceDoesNotBlockAnOtherwiseEligibleOrder() throws Exception {
        var heavy = order(1, "PRD-003", 2, true);
        var general = order(3, "PRD-001", 2, true);
        var occupied = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var holders = new ArrayList<Future<?>>();
            for (int i = 0; i < 3; i++) holders.add(pool.submit(() -> {
                try (var ignored = resources.acquire(new ResourceOwner(UUID.randomUUID(), null, OperationArea.RECEPTION, "Traslado"), Set.of(ResourceType.FORKLIFT), Duration.ZERO)) {
                    occupied.countDown(); release.await(5, TimeUnit.SECONDS);
                }
                return null;
            }));
            try {
                assertTrue(occupied.await(2, TimeUnit.SECONDS));
                try (var lease = scheduler.claimNext().orElseThrow()) {
                    assertEquals(general.id(), lease.orderId());
                    assertEquals(OrderStatus.WAITING_RESOURCES, orders.get(heavy.id()).status());
                }
            } finally { release.countDown(); }
            for (var holder : holders) holder.get();
        }
    }
    @Test void interruptedSelectionDoesNotClaimAndReturnedLeaseRestoresQueue() throws Exception {
        order(1, "PRD-001", 1, true);
        Thread.currentThread().interrupt();
        try { assertTrue(scheduler.claimNext().isEmpty()); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
        try (var lease = scheduler.claimNext().orElseThrow(); var pool = Executors.newFixedThreadPool(1)) {
            pool.submit(() -> assertThrows(IllegalStateException.class, lease::close)).get();
        }
        assertEquals(1, scheduler.snapshot().eligible().size());
    }
    @Test void metricsUseActualHistoryAndMeasuredLeaseTimeInIsolatedWorkerTest() {
        var order = order(2, "PRD-001", 3, true);
        nanos.addAndGet(10_000_000_000L);
        try (var lease = scheduler.claimNext().orElseThrow()) {
            assertEquals(order.id(), lease.orderId());
            orders.transition(order.id(), OrderStatus.WAITING_RESOURCES, OrderStatus.PICKING);
            nanos.addAndGet(20_000_000_000L);
            var previous = OrderStatus.PICKING;
            for (var next : List.of(OrderStatus.PACKING, OrderStatus.SCANNING, OrderStatus.LOADING, OrderStatus.COMPLETED)) {
                orders.transition(order.id(), previous, next); previous = next;
            }
        }
        var metrics = scheduler.snapshot().metrics().getFirst();
        assertEquals(1, metrics.completedOrders());
        assertEquals(10, metrics.averageCompletedWaitSimulatedSeconds());
        assertEquals(20, metrics.averageProcessingSimulatedSeconds());
        assertEquals(20.0 / 30, metrics.resourceUtilization().get(ResourceType.SCANNER), .0001);
    }
}
