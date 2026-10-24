package com.redxela.api.features.inventory.service;

import com.redxela.api.features.inventory.model.*;
import com.redxela.api.features.inventory.model.InventoryOverview.*;
import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import com.redxela.api.shared.exception.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.springframework.stereotype.Service;
import static com.redxela.api.features.inventory.model.InventoryReservation.Status.*;

// Todos los mapas y estados de pedidos se coordinan bajo el monitor reentrante de OrderService
@Service
public class InventoryService {
    private final OrderService orders;
    private final ProductCatalog catalog;
    private final SimulationClock clock;
    private final long capacity;
    private final com.redxela.api.features.storage.service.PhysicalStorage storage;
    private final Map<String, Long> onHand = new HashMap<>();
    private final Map<String, Long> reserved = new HashMap<>();
    private final Map<UUID, InventoryReservation> reservations = new HashMap<>();
    private final Map<UUID, InventoryReceipt> receipts = new HashMap<>();
    public InventoryService(OrderService orders, ProductCatalog catalog, SimulationProperties settings, SimulationClock clock) {
        this.orders = orders; this.catalog = catalog; this.clock = clock; this.capacity = settings.storageCapacity();
        this.storage = new com.redxela.api.features.storage.service.PhysicalStorage(new com.redxela.api.features.storage.service.StorageLayoutService(settings).get());
        for (var product : catalog.list()) { onHand.put(product.id(), 0L); reserved.put(product.id(), 0L); }
    }
    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operación interrumpida antes de modificar inventario");
    }
    public InventoryReceipt receive(UUID arrivalId, String productId, long quantity) {
        if (arrivalId == null || quantity <= 0) throw new IllegalArgumentException("Identificador de entrada y cantidad positiva obligatorios");
        catalog.get(productId);
        return orders.atomically(() -> {
            checkInterrupted();
            var previous = receipts.get(arrivalId);
            if (previous != null) {
                if (!previous.productId().equals(productId) || previous.quantity() != quantity)
                    throw new ConflictException("El identificador de entrada ya se utilizó con otros datos");
                return previous;
            }
            long occupied = onHand.values().stream().mapToLong(Long::longValue).sum();
            if (quantity > capacity - occupied) throw new ConflictException("Capacidad total de almacenamiento insuficiente; la entrada no fue admitida");
            storage.receive(arrivalId, productId, quantity);
            onHand.merge(productId, quantity, Long::sum);
            var reactivated = reconsiderWaitingInternal();
            var receipt = new InventoryReceipt(arrivalId, productId, quantity, Instant.now(), clock.elapsedSeconds(), reactivated);
            receipts.put(arrivalId, receipt);
            return receipt;
        });
    }
    // Comprobar todas las líneas antes de reservar evita reservas parciales y stock negativo
    public boolean reserveIfAvailable(UUID orderId) {
        return orders.atomically(() -> { checkInterrupted(); return reserveInternal(orders.get(orderId)); });
    }
    public boolean reconsiderAdmitted(UUID orderId) {
        return orders.atomically(() -> reserveInternal(orders.get(orderId)));
    }
    private boolean reserveInternal(Order order) {
        var existing = reservations.get(order.id());
        if (existing != null) return existing.status() == RESERVED;
        if (order.status() != OrderStatus.WAITING_INVENTORY || !shortagesInternal(order).isEmpty()) return false;
        var quantities = new LinkedHashMap<String, Long>();
        for (var line : order.lines()) quantities.put(line.product().id(), (long) line.quantity());
        var reservation = new InventoryReservation(order.id(), quantities, RESERVED, Instant.now());
        // La transición puede fallar antes de publicar cantidades; ningún observador entra a mitad de la transacción
        orders.transition(order.id(), OrderStatus.WAITING_INVENTORY, OrderStatus.READY);
        storage.reserve(order.id(), quantities);
        quantities.forEach((product, quantity) -> reserved.merge(product, quantity, Long::sum));
        reservations.put(order.id(), reservation);
        return true;
    }
    public List<UUID> reconsiderWaiting() {
        return orders.atomically(() -> { checkInterrupted(); return reconsiderWaitingInternal(); });
    }
    private List<UUID> reconsiderWaitingInternal() {
        var reactivated = new ArrayList<UUID>();
        // FIFO de reserva; un pedido incompleto no bloquea otros con productos disponibles
        for (var order : orders.list()) if (order.status() == OrderStatus.WAITING_INVENTORY && reserveInternal(order)) reactivated.add(order.id());
        return List.copyOf(reactivated);
    }
    public InventoryReservation reservation(UUID orderId) {
        return orders.atomically(() -> {
            orders.get(orderId);
            var reservation = reservations.get(orderId);
            if (reservation == null) throw new NotFoundException("El pedido todavía no tiene reserva");
            return reservation;
        });
    }
    // Despacho consume una sola vez; no se permite descontar antes de llegar a carga
    public InventoryReservation consume(UUID orderId) {
        return orders.atomically(() -> {
            checkInterrupted();
            var reservation = reservation(orderId);
            if (reservation.status() == CONSUMED) return reservation;
            if (reservation.status() != RESERVED || orders.get(orderId).status() != OrderStatus.LOADING)
                throw new ConflictException("La reserva solo puede consumirse durante la carga");
            storage.consume(orderId);
            reservation.quantities().forEach((product, quantity) -> {
                onHand.merge(product, -quantity, Long::sum); reserved.merge(product, -quantity, Long::sum);
            });
            var consumed = reservation.withStatus(CONSUMED);
            reservations.put(orderId, consumed);
            return consumed;
        });
    }
    // La limpieza deliberadamente ignora la bandera de interrupción para no fugar reservas
    public Order abort(UUID orderId, OrderStatus terminal) {
        if (terminal != OrderStatus.CANCELLED && terminal != OrderStatus.FAILED)
            throw new IllegalArgumentException("Abortar requiere CANCELLED o FAILED");
        return orders.atomically(() -> {
            var current = orders.get(orderId);
            var reservation = reservations.get(orderId);
            if (reservation != null && reservation.status() == CONSUMED)
                throw new ConflictException("No se puede devolver una reserva ya consumida");
            if (current.status() != terminal) current = orders.transition(orderId, current.status(), terminal);
            if (reservation != null && reservation.status() == RESERVED) {
                storage.release(orderId);
                reservation.quantities().forEach((product, quantity) -> reserved.merge(product, -quantity, Long::sum));
                reservations.put(orderId, reservation.withStatus(RELEASED));
                reconsiderWaitingInternal();
            }
            return current;
        });
    }
    public List<Shortage> shortages(UUID orderId) { return orders.atomically(() -> shortagesInternal(orders.get(orderId))); }
    private List<Shortage> shortagesInternal(Order order) {
        var existing = reservations.get(order.id());
        if (existing != null && existing.status() != RELEASED) return List.of();
        var shortages = new ArrayList<Shortage>();
        for (var line : order.lines()) {
            long available = onHand.get(line.product().id()) - reserved.get(line.product().id());
            if (available < line.quantity()) shortages.add(new Shortage(line.product().id(), line.quantity(), available, line.quantity() - available));
        }
        return List.copyOf(shortages);
    }
    public com.redxela.api.features.storage.model.StorageOverview storageSnapshot() { return orders.atomically(storage::snapshot); }
    public InventoryOverview snapshot() {
        return orders.atomically(() -> {
            var products = catalog.list().stream().map(product -> new Stock(product, onHand.get(product.id()), reserved.get(product.id()),
                    onHand.get(product.id()) - reserved.get(product.id()))).toList();
            long total = products.stream().mapToLong(Stock::onHand).sum();
            long allocated = products.stream().mapToLong(Stock::reserved).sum();
            var waiting = orders.list().stream().filter(order -> order.status() == OrderStatus.WAITING_INVENTORY)
                    .map(order -> new WaitingOrder(order.id(), shortagesInternal(order))).toList();
            return new InventoryOverview(Instant.now(), capacity, total, allocated, total - allocated, products, waiting);
        });
    }
}
