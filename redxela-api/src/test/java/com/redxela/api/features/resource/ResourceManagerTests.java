package com.redxela.api.features.resource;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.model.ResourceOverview.*;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.ConflictException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.redxela.api.features.resource.model.ResourceType.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class ResourceManagerTests {
    private final ResourceManager manager = new ResourceManager();
    private ResourceOwner owner(OperationArea area) { return new ResourceOwner(UUID.randomUUID(), null, area, "Operación de prueba"); }
    private ResourceSnapshot snapshot(ResourceType type) {
        return manager.snapshot().resources().stream().filter(resource -> resource.type() == type).findFirst().orElseThrow();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "La condición concurrente no se observó a tiempo");
    }
    private void assertAllFree() {
        for (var resource : manager.snapshot().resources()) {
            assertEquals(resource.capacity(), resource.available());
            assertEquals(0, resource.inUse());
            assertTrue(resource.waiting().isEmpty());
            assertTrue(resource.units().stream().allMatch(unit -> unit.owner() == null && unit.status() == UnitStatus.AVAILABLE));
        }
    }
    @ParameterizedTest @EnumSource(ResourceType.class)
    void exactCapacityOwnershipAndContentionForEveryResource(ResourceType type) throws Exception {
        int capacity = type.capacity();
        var entered = new CountDownLatch(capacity);
        var release = new CountDownLatch(1);
        var concurrent = new AtomicInteger();
        var peak = new AtomicInteger();
        var inUse = ConcurrentHashMap.<String>newKeySet();
        try (var pool = Executors.newFixedThreadPool(capacity * 3)) {
            try {
                var tasks = new ArrayList<Future<?>>();
                for (int i = 0; i < capacity * 3; i++) tasks.add(pool.submit(() -> {
                    try (var lease = manager.acquire(owner(OperationArea.ORDER_PROCESSING), Set.of(type), Duration.ofSeconds(5))) {
                        String unit = lease.assignments().getFirst().unitId();
                        assertTrue(inUse.add(unit), "Unidad asignada simultáneamente a dos operaciones");
                        int count = concurrent.incrementAndGet();
                        peak.accumulateAndGet(count, Math::max);
                        assertTrue(count <= capacity);
                        entered.countDown();
                        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        finally { concurrent.decrementAndGet(); assertTrue(inUse.remove(unit)); }
                    }
                    return null;
                }));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                await(() -> !snapshot(type).waiting().isEmpty());
                var state = snapshot(type);
                assertEquals(capacity, state.inUse());
                assertEquals(0, state.available());
                assertEquals(capacity, state.units().stream().map(unit -> unit.owner().operationId()).distinct().count());
                assertTrue(state.waiting().stream().allMatch(waiter -> waiter.unavailableResources().contains(type)));
                release.countDown();
                for (var task : tasks) task.get(5, TimeUnit.SECONDS);
                assertEquals(capacity, peak.get());
                assertAllFree();
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }
    @Test void receptionAndDispatchUseSameScannerAndInterruptedWaitLeavesNoEntry() throws Exception {
        try (var reception = manager.acquire(owner(OperationArea.RECEPTION), Set.of(SCANNER), Duration.ZERO);
             var pool = Executors.newFixedThreadPool(1)) {
            var finished = new CountDownLatch(1);
            var dispatch = pool.submit(() -> {
                try (var ignored = manager.acquire(owner(OperationArea.DISPATCH), Set.of(SCANNER), Duration.ofSeconds(5))) {
                    fail("No debe obtener el escáner retenido por Recepción");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                catch (TimeoutException ex) { fail(ex); }
                finally { finished.countDown(); }
            });
            await(() -> snapshot(SCANNER).waiting().size() == 1);
            var state = snapshot(SCANNER);
            assertEquals(OperationArea.RECEPTION, state.units().getFirst().owner().area());
            assertEquals(OperationArea.DISPATCH, state.waiting().getFirst().owner().area());
            dispatch.cancel(true);
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertTrue(snapshot(SCANNER).waiting().isEmpty());
            assertEquals(0, snapshot(SCANNER).available());
        }
        try (var dispatch = manager.acquire(owner(OperationArea.DISPATCH), Set.of(SCANNER), Duration.ZERO)) {
            assertEquals(1, dispatch.assignments().size());
        }
        assertAllFree();
    }
    @Test void timeoutDoesNotRetainPartOfARequestOrBlockDisjointWork() throws Exception {
        try (var scanner = manager.acquire(owner(OperationArea.RECEPTION), Set.of(SCANNER), Duration.ZERO);
             var pool = Executors.newFixedThreadPool(2)) {
            var waiting = pool.submit(() -> assertThrows(TimeoutException.class, () ->
                    manager.acquire(owner(OperationArea.DISPATCH), Set.of(SCANNER, FORKLIFT), Duration.ofMillis(300))));
            await(() -> !snapshot(SCANNER).waiting().isEmpty());
            assertEquals(3, snapshot(FORKLIFT).available());
            var unrelated = pool.submit(() -> {
                try (var ignored = manager.acquire(owner(OperationArea.ORDER_PROCESSING), Set.of(PACKING_STATION), Duration.ZERO)) {
                    assertEquals(1, snapshot(PACKING_STATION).inUse());
                }
                return null;
            });
            unrelated.get(2, TimeUnit.SECONDS);
            waiting.get(2, TimeUnit.SECONDS);
            assertTrue(snapshot(SCANNER).waiting().isEmpty());
            assertEquals(3, snapshot(FORKLIFT).available());
        }
        assertAllFree();
    }
    @Test void onlyOwningThreadReleasesAndRepeatedCloseDoesNotAddPermits() throws Exception {
        var owner = owner(OperationArea.DISPATCH);
        try (var lease = manager.acquire(owner, Set.of(SCANNER, LOADING_AREA), Duration.ZERO);
             var pool = Executors.newFixedThreadPool(1)) {
            pool.submit(() -> assertThrows(IllegalStateException.class, lease::close)).get(2, TimeUnit.SECONDS);
            assertEquals(0, snapshot(SCANNER).available());
            assertThrows(ConflictException.class, () -> manager.acquire(owner(OperationArea.RECEPTION), Set.of(FORKLIFT), Duration.ZERO));
            pool.submit(() -> assertThrows(ConflictException.class, () -> manager.acquire(owner, Set.of(PACKING_STATION), Duration.ZERO))).get(2, TimeUnit.SECONDS);
            lease.close();
            lease.close();
            assertAllFree();
        }
        assertAllFree();
    }
    @Test void exceptionAndInterruptionDuringWorkReleaseAllResources() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            try (var lease = manager.acquire(owner(OperationArea.DISPATCH), EnumSet.allOf(ResourceType.class), Duration.ZERO)) {
                assertEquals(6, lease.assignments().size());
                throw new IllegalStateException("Fallo de operación");
            }
        });
        assertAllFree();
        var acquired = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(1)) {
            var work = pool.submit(() -> {
                try (var ignored = manager.acquire(owner(OperationArea.ORDER_PROCESSING), Set.of(SCANNER, FORKLIFT), Duration.ofSeconds(2))) {
                    acquired.countDown();
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                catch (TimeoutException ex) { fail(ex); }
                finally { finished.countDown(); }
            });
            try {
                assertTrue(acquired.await(2, TimeUnit.SECONDS));
                work.cancel(true);
                assertTrue(finished.await(2, TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
        }
        assertAllFree();
    }
    @Test void realOrdersWithReversedResourceIterationFinishWithoutCircularWait() throws Exception {
        var customers = new CustomerService();
        var settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
        var orders = new OrderService(customers, new ProductCatalog(), new SimulationClock(settings), settings);
        var customer = customers.create("Contención", 3);
        var orderA = orders.create(customer.id(), List.of(new OrderSelection("PRD-003", 2)));
        var orderB = orders.create(customer.id(), List.of(new OrderSelection("PRD-003", 4)));
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 100; i++) {
            var order = i % 2 == 0 ? orderA : orderB;
            var types = new ArrayList<>(order.requiredResources());
            if (i % 2 == 0) Collections.reverse(types);
            tasks.add(() -> {
                var owner = new ResourceOwner(UUID.randomUUID(), order.id(), OperationArea.ORDER_PROCESSING, "Pedido pesado");
                try (var lease = manager.acquire(owner, new LinkedHashSet<>(types), Duration.ofSeconds(5))) {
                    assertEquals(order.requiredResources().size(), lease.assignments().size());
                    for (var resource : manager.snapshot().resources()) {
                        assertEquals(resource.capacity(), resource.available() + resource.inUse());
                        assertEquals(resource.inUse(), resource.units().stream().filter(unit -> unit.owner() != null).count());
                    }
                    Thread.sleep(1);
                }
                return null;
            });
        }
        try (var pool = Executors.newFixedThreadPool(12)) {
            for (var future : pool.invokeAll(tasks, 8, TimeUnit.SECONDS)) future.get();
        }
        assertAllFree();
    }
    @Test void invalidRequestsAndImmutableSnapshots() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> manager.acquire(owner(OperationArea.RECEPTION), Set.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> manager.acquire(owner(OperationArea.RECEPTION), Set.of(SCANNER), Duration.ofSeconds(-1)));
        var before = manager.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> before.resources().clear());
        try (var lease = manager.acquire(owner(OperationArea.DISPATCH), Set.of(SCANNER), Duration.ZERO)) {
            assertThrows(UnsupportedOperationException.class, () -> lease.assignments().clear());
            assertEquals(0, snapshot(SCANNER).available());
            assertEquals(1, before.resources().stream().filter(r -> r.type() == SCANNER).findFirst().orElseThrow().available());
        }
        assertAllFree();
    }
}
