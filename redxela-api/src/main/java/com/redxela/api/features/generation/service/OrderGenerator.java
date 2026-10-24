package com.redxela.api.features.generation.service;

import com.redxela.api.features.customer.service.CustomerService;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.order.service.OrderIntakeService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.generation.model.GeneratorSettings;
import com.redxela.api.shared.exception.ConflictException;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;

// Una sola tarea pendiente evita acumular ticks cuando la llegada supera el ritmo de admisión
@Service
public class OrderGenerator implements AutoCloseable {
    public enum State { STOPPED, RUNNING, PAUSED, ERROR, CLOSED }
    private final CustomerService customers;
    private final OrderService orders;
    private final OrderIntakeService intake;
    private final ProductCatalog catalog;
    private final double scale;
    private final BooleanSupplier pressure;
    private final ScheduledThreadPoolExecutor executor;
    private ScheduledFuture<?> next;
    private GeneratorSettings settings = new GeneratorSettings(60, 42, 1, 5);
    private State state = State.STOPPED;
    private Random random;
    private long generated;
    private long throttledTicks;
    private boolean throttled;
    private String lastError;

    @Autowired
    public OrderGenerator(CustomerService customers, OrderService orders, ProductCatalog catalog, SimulationProperties properties, OrderIntakeService intake) {
        this(customers, orders, catalog, properties, intake, () -> {
            var runtime = Runtime.getRuntime();
            return (double) (runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() >= .85;
        });
    }
    public OrderGenerator(CustomerService customers, OrderService orders, ProductCatalog catalog,
                          SimulationProperties properties, OrderIntakeService intake, BooleanSupplier pressure) {
        this.customers = customers; this.orders = orders; this.catalog = catalog; this.intake = intake;
        this.scale = properties.timeScale(); this.pressure = pressure;
        executor = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("redxela-generator-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }
    public synchronized Status start(GeneratorSettings settings) {
        if (state != State.STOPPED && state != State.ERROR) throw new ConflictException("El generador debe estar detenido");
        this.settings = Objects.requireNonNull(settings);
        random = new Random(settings.seed());
        state = State.RUNNING; lastError = null; throttled = false;
        schedule(0);
        return status();
    }
    public synchronized Status pause() {
        if (state != State.RUNNING) throw new ConflictException("El generador no está ejecutándose");
        state = State.PAUSED; cancel(); return status();
    }
    public synchronized Status resume() {
        if (state != State.PAUSED) throw new ConflictException("El generador no está pausado");
        state = State.RUNNING; schedule(0); return status();
    }
    public synchronized Status stop() {
        if (state == State.CLOSED) throw new ConflictException("El generador está cerrado");
        state = State.STOPPED; throttled = false; cancel(); return status();
    }
    private void cancel() { if (next != null) { next.cancel(false); next = null; } }
    private void schedule(long delayMillis) { next = executor.schedule(this::tick, delayMillis, TimeUnit.MILLISECONDS); }
    private synchronized void tick() {
        if (state != State.RUNNING) return;
        try {
            throttled = pressure.getAsBoolean();
            if (throttled) {
                // Se retrasa una llegada aún no admitida; jamás se elimina un pedido ya registrado
                throttledTicks++; schedule(1000); return;
            }
            int level = (int) (generated % 5) + 1;
            var products = new ArrayList<>(catalog.list());
            Collections.shuffle(products, random);
            int lineCount = 1 + random.nextInt(Math.min(3, products.size()));
            var selections = products.subList(0, lineCount).stream().map(product ->
                    new OrderSelection(product.id(), settings.minUnits() + random.nextInt(settings.maxUnits() - settings.minUnits() + 1))).toList();
            var customer = customers.create("Cliente automático " + (generated + 1), level);
            intake.create(customer.id(), selections, OrderSource.AUTOMATIC);
            generated++;
            // Jitter de 50–150 % y suelo de 20 ms reales limitan la frecuencia, no el total acumulado
            long delay = Math.max(20, Math.round(settings.intervalSimulatedSeconds() / scale * 1000 * (.5 + random.nextDouble())));
            schedule(delay);
        } catch (RuntimeException ex) {
            state = State.ERROR; lastError = "Se detuvo la generación por un error; los pedidos admitidos se conservan";
            LoggerFactory.getLogger(OrderGenerator.class).error("Error en el generador", ex);
        }
    }
    public synchronized Status status() {
        return new Status(state, settings, generated, orders.count(), scale, throttled, throttledTicks, lastError);
    }
    public record Status(State state, GeneratorSettings settings, long generatedOrders, long totalOrders,
                         double timeScale, boolean throttled, long throttledTicks, String lastError) {}
    @Override @PreDestroy public void close() {
        synchronized (this) { state = State.CLOSED; cancel(); executor.shutdownNow(); }
        try { if (!executor.awaitTermination(3, TimeUnit.SECONDS)) throw new IllegalStateException("El generador no terminó"); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
