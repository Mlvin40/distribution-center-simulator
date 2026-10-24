package com.redxela.api.features.inventory;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.inventory.model.*;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.*;
import com.redxela.api.features.product.model.MerchandiseType;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.scheduling.service.OrderScheduler;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class InventoryServiceTests {
    private final CustomerService customers = new CustomerService();
    private final ProductCatalog catalog = new ProductCatalog();
    private final SimulationProperties settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
    private final SimulationClock clock = new SimulationClock(settings);
    private final OrderService orders = new OrderService(customers, catalog, clock, settings);
    private final InventoryService inventory = new InventoryService(orders, catalog, settings, clock);
    private final OrderIntakeService intake = new OrderIntakeService(orders, inventory);
    private final UUID customer = customers.create("Inventario", 3).id();
    private Order order(OrderSelection... lines) { return intake.create(customer, List.of(lines), OrderSource.MANUAL); }
    private void receive(String product, long quantity) { inventory.receive(UUID.randomUUID(), product, quantity); }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    private void assertCoherent() {
        var snapshot = inventory.snapshot();
        assertTrue(snapshot.onHand() <= snapshot.capacity());
        assertEquals(snapshot.onHand(), snapshot.reserved() + snapshot.available());
        assertEquals(snapshot.onHand(), snapshot.products().stream().mapToLong(InventoryOverview.Stock::onHand).sum());
        for (var product : snapshot.products()) {
            assertTrue(product.available() >= 0 && product.reserved() >= 0);
            assertEquals(product.onHand(), product.reserved() + product.available());
        }
    }
    @Test void multipleProductsReserveAllOrNoneAndMissingOrderDoesNotBlockOthers() {
        receive("PRD-001", 5);
        var waiting = order(new OrderSelection("PRD-001", 3), new OrderSelection("PRD-002", 2));
        assertEquals(OrderStatus.WAITING_INVENTORY, waiting.status());
        assertEquals(0, inventory.snapshot().reserved());
        assertEquals(2, inventory.shortages(waiting.id()).getFirst().missing());
        var ready = order(new OrderSelection("PRD-001", 5));
        assertEquals(OrderStatus.READY, ready.status());
        receive("PRD-002", 2);
        assertEquals(OrderStatus.WAITING_INVENTORY, orders.get(waiting.id()).status());
        var receipt = inventory.receive(UUID.randomUUID(), "PRD-001", 3);
        assertEquals(List.of(waiting.id()), receipt.reactivatedOrders());
        assertEquals(OrderStatus.READY, orders.get(waiting.id()).status());
        assertTrue(inventory.shortages(waiting.id()).isEmpty());
        assertEquals(10, inventory.snapshot().reserved());
        assertCoherent();
    }
    @Test void concurrentMultiProductReservationsNeverExceedAvailableStock() throws Exception {
        receive("PRD-001", 50); receive("PRD-002", 50);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Order>>();
            for (int i = 0; i < 100; i++) tasks.add(() -> order(new OrderSelection("PRD-001", 2), new OrderSelection("PRD-002", 3)));
            var ids = new HashSet<UUID>();
            for (var task : pool.invokeAll(tasks)) ids.add(task.get().id());
            assertEquals(100, ids.size());
        }
        assertEquals(16, orders.list().stream().filter(order -> order.status() == OrderStatus.READY).count());
        assertEquals(84, inventory.snapshot().waitingOrders().size());
        assertEquals(80, inventory.snapshot().reserved());
        assertEquals(20, inventory.snapshot().available());
        assertCoherent();
    }
    @Test void repeatedConcurrentReservationAndReceiptRetriesAreIdempotent() throws Exception {
        var receiptId = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<InventoryReceipt>>();
            for (int i = 0; i < 100; i++) tasks.add(() -> inventory.receive(receiptId, "PRD-001", 20));
            var responses = pool.invokeAll(tasks);
            var first = responses.getFirst().get();
            for (var task : responses) assertEquals(first, task.get());
            var order = order(new OrderSelection("PRD-001", 5));
            var reservations = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 100; i++) reservations.add(() -> inventory.reserveIfAvailable(order.id()));
            for (var task : pool.invokeAll(reservations)) assertTrue(task.get());
            assertEquals(20, inventory.snapshot().onHand());
            assertEquals(5, inventory.snapshot().reserved());
        }
        assertCoherent();
    }
    @Test void concurrentEntriesAndAdmissionsDoNotLoseAvailabilityNotifications() throws Exception {
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> { receive("PRD-001", 1); assertCoherent(); return null; });
            tasks.add(() -> { order(new OrderSelection("PRD-001", 1)); assertCoherent(); return null; });
        }
        Collections.shuffle(tasks, new Random(42));
        try (var pool = Executors.newFixedThreadPool(12)) {
            for (var result : pool.invokeAll(tasks)) result.get();
        }
        assertEquals(100, orders.count());
        assertEquals(100, inventory.snapshot().onHand());
        assertEquals(100, inventory.snapshot().reserved());
        assertEquals(0, inventory.snapshot().available());
        assertTrue(inventory.snapshot().waitingOrders().isEmpty());
        assertTrue(orders.list().stream().allMatch(order -> order.status() == OrderStatus.READY));
        assertCoherent();
    }
    @Test void invalidEntriesCapacityAndConflictingIdDoNotMutateStock() {
        var id = UUID.randomUUID();
        inventory.receive(id, "PRD-001", 10);
        var before = inventory.snapshot().products();
        assertThrows(ConflictException.class, () -> inventory.receive(id, "PRD-001", 11));
        assertThrows(NotFoundException.class, () -> inventory.receive(UUID.randomUUID(), "missing", 1));
        assertThrows(IllegalArgumentException.class, () -> inventory.receive(UUID.randomUUID(), "PRD-001", 0));
        assertThrows(ConflictException.class, () -> inventory.receive(UUID.randomUUID(), "PRD-001", Long.MAX_VALUE));
        assertEquals(before, inventory.snapshot().products());
        receive("PRD-002", 590);
        assertEquals(600, inventory.snapshot().onHand());
        assertThrows(ConflictException.class, () -> receive("PRD-003", 1));
        assertCoherent();
    }
    @Test void consumingRequiresLoadingAndCannotDeliverTheSameUnitsTwice() {
        receive("PRD-001", 5);
        var order = order(new OrderSelection("PRD-001", 5));
        assertThrows(ConflictException.class, () -> inventory.consume(order.id()));
        var previous = OrderStatus.READY;
        for (var next : List.of(OrderStatus.WAITING_RESOURCES, OrderStatus.PICKING, OrderStatus.PACKING, OrderStatus.SCANNING, OrderStatus.LOADING)) {
            orders.transition(order.id(), previous, next); previous = next;
        }
        assertEquals(InventoryReservation.Status.CONSUMED, inventory.consume(order.id()).status());
        assertEquals(InventoryReservation.Status.CONSUMED, inventory.consume(order.id()).status());
        assertEquals(0, inventory.snapshot().onHand());
        assertEquals(0, inventory.snapshot().reserved());
        assertThrows(ConflictException.class, () -> inventory.abort(order.id(), OrderStatus.CANCELLED));
        assertCoherent();
    }
    @Test void abortRestoresReservationExactlyOnceAndReactivatesAnotherOrder() {
        receive("PRD-001", 5);
        var first = order(new OrderSelection("PRD-001", 5));
        var second = order(new OrderSelection("PRD-001", 5));
        assertEquals(OrderStatus.WAITING_INVENTORY, second.status());
        inventory.abort(first.id(), OrderStatus.FAILED);
        inventory.abort(first.id(), OrderStatus.FAILED);
        assertEquals(InventoryReservation.Status.RELEASED, inventory.reservation(first.id()).status());
        assertEquals(OrderStatus.READY, orders.get(second.id()).status());
        assertEquals(5, inventory.snapshot().reserved());
        assertEquals(5, inventory.snapshot().onHand());
        assertThrows(ConflictException.class, () -> inventory.abort(first.id(), OrderStatus.CANCELLED));
        assertEquals(5, inventory.snapshot().reserved());
        assertCoherent();
    }
    @Test void interruptedAdmissionAndEntryDoNotPartiallyChangeState() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> receive("PRD-001", 5));
            assertThrows(CancellationException.class, () -> order(new OrderSelection("PRD-001", 1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(0, inventory.snapshot().onHand());
        assertEquals(0, orders.count());
    }
    @Test void interruptedWorkerCooperativelyReleasesStockAndRealResources() throws Exception {
        receive("PRD-001", 5);
        var first = order(new OrderSelection("PRD-001", 5));
        var second = order(new OrderSelection("PRD-001", 5));
        var resources = new ResourceManager();
        var scheduler = new OrderScheduler(orders, resources, clock);
        var acquired = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(1)) {
            var task = pool.submit(() -> {
                try (var lease = scheduler.claimNext().orElseThrow()) {
                    assertEquals(first.id(), lease.orderId());
                    acquired.countDown();
                    try { new CountDownLatch(1).await(); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    finally { inventory.abort(first.id(), OrderStatus.FAILED); }
                } finally { finished.countDown(); }
            });
            try {
                assertTrue(acquired.await(2, TimeUnit.SECONDS));
                task.cancel(true);
                assertTrue(finished.await(2, TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
        }
        assertEquals(OrderStatus.FAILED, orders.get(first.id()).status());
        assertEquals(OrderStatus.READY, orders.get(second.id()).status());
        assertEquals(5, inventory.snapshot().reserved());
        assertTrue(resources.snapshot().resources().stream().allMatch(resource -> resource.inUse() == 0));
        assertCoherent();
    }
    @Test void fiveProfilesAreIndependentOfServiceAndCompeteForTwoRealResources() throws Exception {
        assertEquals(5, Arrays.stream(MerchandiseType.values()).map(MerchandiseType::requiredResources).distinct().count());
        receive("PRD-003", 5); receive("PRD-005", 5);
        var heavy = order(new OrderSelection("PRD-003", 1));
        var bulky = intake.create(customers.create("Exprés", 1).id(), List.of(new OrderSelection("PRD-005", 1)), OrderSource.MANUAL);
        for (var order : List.of(heavy, bulky)) assertTrue(order.requiredResources().containsAll(Set.of(ResourceType.FORKLIFT, ResourceType.LOADING_AREA)));
        var resources = new ResourceManager();
        var occupied = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Future<?>>();
            for (var order : List.of(heavy, bulky, order(new OrderSelection("PRD-003", 1)), order(new OrderSelection("PRD-005", 1)))) tasks.add(pool.submit(() -> {
                var owner = new ResourceOwner(UUID.randomUUID(), order.id(), OperationArea.ORDER_PROCESSING, "Etapa de traslado y carga de mercancía");
                try (var lease = resources.acquire(owner, Set.of(ResourceType.FORKLIFT, ResourceType.LOADING_AREA), Duration.ofSeconds(5))) {
                    assertEquals(2, lease.assignments().size());
                    occupied.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
                }
                return null;
            }));
            try {
                assertTrue(occupied.await(2, TimeUnit.SECONDS));
                await(() -> resources.snapshot().resources().get(1).waiting().size() == 2);
                assertEquals(2, resources.snapshot().resources().get(1).inUse());
                assertEquals(2, resources.snapshot().resources().get(3).inUse());
            } finally { release.countDown(); }
            for (var task : tasks) task.get();
        }
        assertTrue(resources.snapshot().resources().stream().allMatch(resource -> resource.inUse() == 0));
    }
}
