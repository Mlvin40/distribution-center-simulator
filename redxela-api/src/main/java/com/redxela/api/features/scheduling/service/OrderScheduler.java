package com.redxela.api.features.scheduling.service;

import com.redxela.api.features.order.model.*;
import com.redxela.api.features.order.service.OrderService;
import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.service.ResourceManager;
import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.scheduling.model.SchedulingOverview.*;
import com.redxela.api.features.simulation.service.SimulationClock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

// El monitor de pedidos protege conjuntamente política, cola y selección exclusiva
@Service
public class OrderScheduler {
    private final OrderService orders;
    private final ResourceManager resources;
    private final SimulationClock clock;
    private SchedulingPolicy policy = SchedulingPolicy.STRICT_PRIORITY;
    private final java.util.ArrayDeque<Selection> selections = new java.util.ArrayDeque<>();
    public record Selection(UUID orderId, SchedulingPolicy policy, double simulatedSeconds) {}
    public java.util.List<Selection> selections() { return orders.atomically(() -> java.util.List.copyOf(selections)); }
    private final Map<UUID, DispatchLease> claims = new HashMap<>();
    private final Map<UUID, SchedulingPolicy> attribution = new HashMap<>();
    private final EnumMap<SchedulingPolicy, Counters> counters = new EnumMap<>(SchedulingPolicy.class);
    private final double observationOrigin;
    public OrderScheduler(OrderService orders, ResourceManager resources, SimulationClock clock) {
        this.orders = orders; this.resources = resources; this.clock = clock;
        observationOrigin = clock.elapsedSeconds();
        for (var value : SchedulingPolicy.values()) counters.put(value, new Counters());
    }
    public SchedulingOverview changePolicy(SchedulingPolicy next) {
        Objects.requireNonNull(next);
        return orders.atomically(() -> { policy = next; return snapshot(); });
    }
    private List<Order> candidates() {
        return orders.list().stream().filter(order -> !claims.containsKey(order.id())
                && (order.status() == OrderStatus.READY || order.status() == OrderStatus.WAITING_RESOURCES)).toList();
    }
    // Selección atómica del trabajador: el ciclo operativo decide las transiciones posteriores
    public Optional<DispatchLease> claimNext() { return claimNext(false); }
    public Optional<DispatchLease> claimForProcessing() { return claimNext(true); }
    private Optional<DispatchLease> claimNext(boolean staged) {
        return orders.atomically(() -> {
            if (Thread.currentThread().isInterrupted()) return Optional.empty();
            double now = clock.elapsedSeconds();
            var comparator = policy.comparator(now);
            var queue = candidates().stream().sorted((a, b) -> comparator.compare(SchedulingCandidate.from(a), SchedulingCandidate.from(b))).toList();
            for (var candidate : queue) {
                try {
                    var owner = new ResourceOwner(UUID.randomUUID(), candidate.id(), OperationArea.ORDER_PROCESSING, "Selección de pedido");
                    var requested = staged ? java.util.EnumSet.of(ResourceType.WAREHOUSE_WORKER) : candidate.requiredResources();
                    if (staged && candidate.requiredResources().contains(ResourceType.FORKLIFT)) requested.add(ResourceType.FORKLIFT);
                    var resourceLease = resources.acquire(owner, requested, Duration.ZERO);
                    try {
                        if (candidate.status() == OrderStatus.READY)
                            orders.transition(candidate.id(), OrderStatus.READY, OrderStatus.WAITING_RESOURCES);
                        double acquiredAt = clock.elapsedSeconds();
                        var lease = new DispatchLease(candidate.id(), policy, acquiredAt, resourceLease);
                        claims.put(candidate.id(), lease);
                        selections.addLast(new Selection(candidate.id(), policy, acquiredAt));
                        while (selections.size() > 300) selections.removeFirst();
                        attribution.put(candidate.id(), policy);
                        var metrics = counters.get(policy);
                        metrics.selections++; metrics.selectionWait += SchedulingCandidate.from(candidate).waitSeconds(acquiredAt);
                        return Optional.of(lease);
                    } catch (RuntimeException ex) { resourceLease.close(); throw ex; }
                } catch (TimeoutException ex) {
                    if (candidate.status() == OrderStatus.READY)
                        orders.transition(candidate.id(), OrderStatus.READY, OrderStatus.WAITING_RESOURCES);
                    // Un conjunto ocupado no impide elegir otro pedido que sí puede adquirir sus recursos
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return Optional.empty(); }
            }
            return Optional.empty();
        });
    }
    public SchedulingOverview snapshot() {
        return orders.atomically(() -> {
            double now = clock.elapsedSeconds();
            var comparator = policy.comparator(now);
            var all = orders.list();
            var eligible = candidates().stream().sorted((a,b) -> comparator.compare(SchedulingCandidate.from(a), SchedulingCandidate.from(b)))
                    .map(order -> entry(order, now, order.status() == OrderStatus.READY ? "READY" : "WAITING_RESOURCES")).toList();
            var waiting = all.stream().filter(order -> order.status() == OrderStatus.WAITING_INVENTORY)
                    .map(order -> entry(order, now, "WAITING_INVENTORY")).toList();
            var selected = all.stream().filter(order -> claims.containsKey(order.id())).map(order -> entry(order, now, "CLAIMED")).toList();
            return new SchedulingOverview(policy, policy.criterion(), "Llegada simulada ascendente y UUID ascendente",
                    now, eligible, waiting, selected, metrics(all, now));
        });
    }
    private Entry entry(Order order, double now, String reason) {
        var candidate = SchedulingCandidate.from(order);
        double waitEnd = order.history().stream().filter(event -> event.status() == OrderStatus.PICKING)
                .findFirst().map(OrderTransition::simulatedSeconds).orElse(now);
        return new Entry(order, candidate.waitSeconds(waitEnd), policy.effectivePriority(candidate, waitEnd), candidate.targetMissed(waitEnd), reason);
    }
    private List<PolicyMetrics> metrics(List<Order> all, double now) {
        var result = new ArrayList<PolicyMetrics>();
        for (var value : SchedulingPolicy.values()) {
            var count = counters.get(value);
            var completed = all.stream().filter(order -> order.status() == OrderStatus.COMPLETED && attribution.get(order.id()) == value).toList();
            double wait = 0, processing = 0; long missed = 0;
            for (var order : completed) {
                double start = order.history().stream().filter(event -> event.status() == OrderStatus.PICKING).findFirst().orElseThrow().simulatedSeconds();
                wait += SchedulingCandidate.from(order).waitSeconds(start);
                processing += order.history().getLast().simulatedSeconds() - start;
                if (SchedulingCandidate.from(order).targetMissed(start)) missed++;
            }
            var busy = new EnumMap<ResourceType, Double>(count.busy);
            for (var lease : claims.values()) if (lease.policy == value && !lease.resourcesClosed)
                for (var assignment : lease.resources.assignments()) busy.merge(assignment.type(), now - lease.acquiredAt, Double::sum);
            var utilization = new EnumMap<ResourceType, Double>(ResourceType.class);
            double elapsed = Math.max(0, now - observationOrigin);
            for (var type : ResourceType.values()) utilization.put(type, elapsed == 0 ? 0 : busy.getOrDefault(type, 0.0) / (elapsed * type.capacity()));
            result.add(new PolicyMetrics(value, count.selections, completed.size(), count.selections == 0 ? 0 : count.selectionWait / count.selections,
                    completed.isEmpty() ? null : wait / completed.size(), completed.isEmpty() ? null : processing / completed.size(), missed, utilization));
        }
        return result;
    }
    public void recordUsage(SchedulingPolicy selected, java.util.List<ResourceAssignment> assignments, double duration) {
        orders.atomically(() -> { for (var assignment : assignments) counters.get(selected).busy.merge(assignment.type(), Math.max(0,duration), Double::sum); return null; });
    }
    private static class Counters {
        long selections; double selectionWait;
        final EnumMap<ResourceType, Double> busy = new EnumMap<>(ResourceType.class);
    }
    // La devolución se realiza desde el trabajador propietario, incluso ante cancelación o excepción
    public final class DispatchLease implements AutoCloseable {
        private final UUID orderId;
        private final SchedulingPolicy policy;
        private final double acquiredAt;
        private final ResourceManager.ResourceLease resources;
        private final Thread ownerThread = Thread.currentThread();
        private boolean closed;
        private boolean resourcesClosed;
        private DispatchLease(UUID orderId, SchedulingPolicy policy, double acquiredAt, ResourceManager.ResourceLease resources) {
            this.orderId = orderId; this.policy = policy; this.acquiredAt = acquiredAt; this.resources = resources;
        }
        public UUID orderId() { return orderId; }
        public SchedulingPolicy policy() { return policy; }
        public List<ResourceAssignment> assignments() { return resources.assignments(); }
        public void releaseResources() {
            if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Hilo propietario requerido");
            orders.atomically(() -> {
                if (!resourcesClosed) {
                    resources.close();
                    recordUsage(policy, resources.assignments(), clock.elapsedSeconds() - acquiredAt);
                    resourcesClosed = true;
                }
                return null;
            });
        }
        @Override public void close() {
            if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Solo el trabajador propietario puede devolver la selección");
            orders.atomically(() -> {
                if (closed) return null;
                releaseResources();
                claims.remove(orderId);
                if (orders.get(orderId).status() == OrderStatus.WAITING_RESOURCES)
                    orders.transition(orderId, OrderStatus.WAITING_RESOURCES, OrderStatus.READY);
                closed = true;
                return null;
            });
        }
    }
}
