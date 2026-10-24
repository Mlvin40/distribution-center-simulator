package com.redxela.api.features.scheduling.model;

import com.redxela.api.features.customer.model.ServiceLevel;
import com.redxela.api.features.order.model.Order;
import com.redxela.api.features.resource.model.ResourceType;
import java.util.*;

public record SchedulingCandidate(UUID id, ServiceLevel serviceLevel, long totalUnits,
                                  double arrivalSimulatedSeconds, long workSimulatedSeconds,
                                  Set<ResourceType> requiredResources) {
    public SchedulingCandidate {
        if (id == null || serviceLevel == null || totalUnits <= 0 || workSimulatedSeconds <= 0
                || !Double.isFinite(arrivalSimulatedSeconds) || arrivalSimulatedSeconds < 0
                || requiredResources == null || requiredResources.isEmpty())
            throw new IllegalArgumentException("Candidato de planificación inválido");
        requiredResources = Set.copyOf(requiredResources);
    }
    public static SchedulingCandidate from(Order order) {
        return new SchedulingCandidate(order.id(), order.serviceLevel(), order.totalUnits(),
                order.history().getFirst().simulatedSeconds(), order.baseWorkSimulatedSeconds(), order.requiredResources());
    }
    public double waitSeconds(double now) { return Math.max(0, now - arrivalSimulatedSeconds); }
    public boolean targetMissed(double now) { return waitSeconds(now) > serviceLevel.targetWaitMinutes() * 60.0; }
}
