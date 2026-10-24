package com.redxela.api.features.generation;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.order.service.OrderIntakeService;
import com.redxela.api.features.inventory.service.InventoryService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.features.generation.model.GeneratorSettings;
import com.redxela.api.features.generation.service.OrderGenerator;
import com.redxela.api.shared.exception.ConflictException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class OrderGeneratorTests {
    private final CustomerService customers = new CustomerService();
    private final ProductCatalog catalog = new ProductCatalog();
    private final SimulationProperties settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
    private final OrderService orders = new OrderService(customers, catalog, new SimulationClock(settings), settings);
    private final OrderIntakeService intake = new OrderIntakeService(orders, new InventoryService(orders, catalog, settings, new SimulationClock(settings)));
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }
    @Test void allLevelsManualInParallelPauseResumeAndStopWithoutLostIds() throws Exception {
        try (var generator = new OrderGenerator(customers, orders, catalog, settings, intake, () -> false);
             var pool = Executors.newFixedThreadPool(8)) {
            generator.start(new GeneratorSettings(.1, 42, 1, 5));
            var customer = customers.create("Manual", 2);
            var tasks = new ArrayList<Callable<UUID>>();
            for (int i = 0; i < 200; i++) tasks.add(() -> orders.create(customer.id(), List.of(new OrderSelection("PRD-001", 2))).id());
            for (var task : pool.invokeAll(tasks)) assertNotNull(task.get());
            await(() -> generator.status().generatedOrders() >= 20);
            generator.pause();
            long paused = generator.status().generatedOrders();
            Thread.sleep(70);
            assertEquals(paused, generator.status().generatedOrders());
            assertEquals(OrderGenerator.State.PAUSED, generator.status().state());
            generator.resume();
            await(() -> generator.status().generatedOrders() > paused);
            generator.stop();
            long stopped = generator.status().generatedOrders();
            Thread.sleep(70);
            assertEquals(stopped, generator.status().generatedOrders());
            var all = orders.list();
            assertEquals(stopped + 200, all.size());
            assertEquals(all.size(), all.stream().map(Order::id).distinct().count());
            var automatic = all.stream().filter(order -> order.source() == OrderSource.AUTOMATIC).toList();
            assertEquals(5, automatic.stream().map(Order::serviceLevel).distinct().count());
            assertEquals(5, automatic.stream().flatMap(order -> order.lines().stream()).map(line -> line.product().id()).distinct().count());
            assertTrue(automatic.stream().map(Order::totalUnits).distinct().count() > 1);
            assertTrue(all.stream().allMatch(order -> order.status() == OrderStatus.WAITING_INVENTORY));
            assertThrows(ConflictException.class, generator::resume);
        }
    }
    @Test void memoryPressureDelaysUnadmittedArrivalsAndRecoversWithoutDroppingOrders() throws Exception {
        var pressure = new AtomicBoolean(true);
        try (var generator = new OrderGenerator(customers, orders, catalog, settings, intake, pressure::get)) {
            generator.start(new GeneratorSettings(.1, 9, 1, 2));
            await(() -> generator.status().throttledTicks() > 0);
            assertEquals(0, orders.count());
            assertTrue(generator.status().throttled());
            pressure.set(false);
            await(() -> orders.count() >= 5);
            generator.stop();
            assertEquals(generator.status().generatedOrders(), orders.count());
        }
    }
    @Test void workerErrorIsVisibleAndCanBeRestartedWithoutLosingAdmittedOrders() throws Exception {
        var failOnce = new AtomicBoolean(true);
        try (var generator = new OrderGenerator(customers, orders, catalog, settings, intake, () -> {
            if (failOnce.getAndSet(false)) throw new IllegalStateException("Fallo controlado de prueba");
            return false;
        })) {
            generator.start(new GeneratorSettings(.1, 3, 1, 1));
            await(() -> generator.status().state() == OrderGenerator.State.ERROR);
            assertNotNull(generator.status().lastError());
            assertEquals(0, orders.count());
            generator.start(new GeneratorSettings(.1, 3, 1, 1));
            await(() -> orders.count() >= 5);
            generator.stop();
            assertEquals(generator.status().generatedOrders(), orders.count());
        }
    }
    @Test void closeStopsWorkerAndInvalidLifecycleDoesNotScheduleTwice() throws Exception {
        var generator = new OrderGenerator(customers, orders, catalog, settings, intake, () -> false);
        generator.start(new GeneratorSettings(.1, 3, 1, 1));
        assertThrows(ConflictException.class, () -> generator.start(new GeneratorSettings(.1, 3, 1, 1)));
        await(() -> orders.count() > 0);
        generator.close();
        long count = orders.count();
        Thread.sleep(60);
        assertEquals(count, orders.count());
        assertEquals(OrderGenerator.State.CLOSED, generator.status().state());
        assertThrows(ConflictException.class, generator::resume);
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(Double.NaN, 1, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new GeneratorSettings(1, 1, 4, 3));
    }
}
