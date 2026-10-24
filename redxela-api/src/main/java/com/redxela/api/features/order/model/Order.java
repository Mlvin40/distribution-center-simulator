package com.redxela.api.features.order.model;

import com.redxela.api.features.customer.model.ServiceLevel;
import com.redxela.api.features.resource.model.ResourceType;
import com.redxela.api.shared.exception.ConflictException;
import java.time.Instant;
import java.util.*;

// Cada cambio reemplaza el agregado completo; ningún lector observa una transición parcial
public record Order(UUID id, OrderSource source, UUID customerId, ServiceLevel serviceLevel, List<OrderLine> lines,
                    long totalUnits, Set<ResourceType> requiredResources, long baseWorkSimulatedSeconds,
                    OrderStatus status, Instant createdAt, Instant processingStartedAt,
                    Instant completedAt, List<OrderTransition> history) {
    public Order {
        lines = List.copyOf(lines);
        requiredResources = Set.copyOf(requiredResources);
        history = List.copyOf(history);
    }
    public Order transitionTo(OrderStatus next, Instant realTime, double simulatedSeconds) {
        if (!status.canTransitionTo(next)) throw new ConflictException("Transición de pedido no permitida");
        var timeline = new ArrayList<>(history);
        timeline.add(new OrderTransition(next, realTime, simulatedSeconds));
        return new Order(id, source, customerId, serviceLevel, lines, totalUnits, requiredResources,
                baseWorkSimulatedSeconds, next, createdAt,
                next == OrderStatus.PICKING && processingStartedAt == null ? realTime : processingStartedAt,
                next == OrderStatus.COMPLETED ? realTime : completedAt, timeline);
    }
}
