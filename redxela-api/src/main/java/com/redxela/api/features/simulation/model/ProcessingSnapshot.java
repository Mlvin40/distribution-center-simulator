package com.redxela.api.features.simulation.model;

import com.redxela.api.features.order.model.OrderStatus;
import java.util.UUID;

public record ProcessingSnapshot(UUID orderId, OrderStatus stage, double workedSimulatedSeconds,
        double progressPercent, double remainingWorkSimulatedSeconds, boolean waitingResources) {}
