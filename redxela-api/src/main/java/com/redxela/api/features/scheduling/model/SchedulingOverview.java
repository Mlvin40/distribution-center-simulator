package com.redxela.api.features.scheduling.model;

import com.redxela.api.features.order.model.Order;
import java.util.*;

public record SchedulingOverview(SchedulingPolicy policy, String criterion, String tieBreaker,
                                 double simulatedSeconds, List<Entry> eligible, List<Entry> waitingInventory,
                                 List<Entry> claimed, List<PolicyMetrics> metrics) {
    public SchedulingOverview {
        eligible = List.copyOf(eligible); waitingInventory = List.copyOf(waitingInventory);
        claimed = List.copyOf(claimed); metrics = List.copyOf(metrics);
    }
    public record Entry(Order order, double waitingSimulatedSeconds, int effectivePriority, boolean targetMissed, String reason) {}
    public record PolicyMetrics(SchedulingPolicy policy, long selections, long completedOrders,
                               double averageSelectionWaitSimulatedSeconds, Double averageCompletedWaitSimulatedSeconds,
                               Double averageProcessingSimulatedSeconds, long missedCompletedTargets,
                               Map<com.redxela.api.features.resource.model.ResourceType, Double> resourceUtilization) {
        public PolicyMetrics { resourceUtilization = Map.copyOf(resourceUtilization); }
    }
}
