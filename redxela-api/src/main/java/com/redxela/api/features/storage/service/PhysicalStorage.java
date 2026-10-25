package com.redxela.api.features.storage.service;

import com.redxela.api.features.storage.model.*;
import com.redxela.api.features.storage.model.StorageOverview.*;
import com.redxela.api.shared.exception.ConflictException;
import java.util.*;

// Acceso exclusivo bajo el monitor de OrderService: stock, reservas y ubicaciones forman una transacción
public final class PhysicalStorage {
    private final StorageLayout layout;
    private final Map<String, List<Slice>> locations = new LinkedHashMap<>();
    private static final class Slice {
        final UUID lot; final String product; long quantity;
        final Map<UUID, Long> reservations = new HashMap<>();
        Slice(UUID lot, String product, long quantity) { this.lot = lot; this.product = product; this.quantity = quantity; }
        long reserved() { return reservations.values().stream().mapToLong(Long::longValue).sum(); }
    }
    public PhysicalStorage(StorageLayout layout) {
        this.layout = layout;
        layout.locations().forEach(location -> locations.put(location.id(), new ArrayList<>()));
    }
    public void receive(UUID lot, String product, long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Cantidad positiva obligatoria");
        if (quantity > snapshot().availableCapacity()) throw new ConflictException("Capacidad física insuficiente");
        // Se valida el lote completo antes de publicar cualquier fragmento; se aprovechan huecos separados
        long remaining = quantity;
        for (var location : layout.locations()) {
            var slices = locations.get(location.id());
            long free = location.capacity() - slices.stream().mapToLong(slice -> slice.quantity).sum();
            long assigned = Math.min(remaining, free);
            if (assigned > 0) { slices.add(new Slice(lot, product, assigned)); remaining -= assigned; }
            if (remaining == 0) break;
        }
    }
    public void reserve(UUID order, Map<String, Long> quantities) {
        for (var entry : quantities.entrySet()) {
            long available = locations.values().stream().flatMap(List::stream).filter(s -> s.product.equals(entry.getKey()))
                    .mapToLong(s -> s.quantity - s.reserved()).sum();
            if (available < entry.getValue()) throw new ConflictException("Existencias físicas insuficientes");
        }
        for (var entry : quantities.entrySet()) {
            long remaining = entry.getValue();
            for (var slices : locations.values()) for (var slice : slices) {
                if (!slice.product.equals(entry.getKey()) || remaining == 0) continue;
                long assigned = Math.min(remaining, slice.quantity - slice.reserved());
                if (assigned > 0) { slice.reservations.merge(order, assigned, Long::sum); remaining -= assigned; }
            }
        }
    }
    public void release(UUID order) { locations.values().forEach(slices -> slices.forEach(s -> s.reservations.remove(order))); }
    public void consume(UUID order) {
        locations.values().forEach(slices -> {
            for (var slice : slices) { var amount = slice.reservations.remove(order); if (amount != null) slice.quantity -= amount; }
            slices.removeIf(slice -> slice.quantity == 0);
        });
    }
    public StorageOverview snapshot() {
        var result = new ArrayList<LocationStock>();
        for (var location : layout.locations()) {
            var slices = locations.get(location.id());
            long occupied = slices.stream().mapToLong(s -> s.quantity).sum();
            long reserved = slices.stream().mapToLong(Slice::reserved).sum();
            result.add(new LocationStock(location, occupied, reserved, location.capacity() - occupied,
                    slices.stream().map(s -> new LotStock(s.lot, s.product, s.quantity, s.reserved())).toList()));
        }
        long occupied = result.stream().mapToLong(LocationStock::occupied).sum();
        return new StorageOverview(layout.totalCapacity(), occupied, result.stream().mapToLong(LocationStock::reserved).sum(),
                layout.totalCapacity() - occupied, result);
    }
}
