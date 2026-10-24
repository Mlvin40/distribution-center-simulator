package com.redxela.api.features.resource.model;

import java.time.Instant;
import java.util.*;

// La instantánea completa se copia bajo el mismo lock que protege asignación y liberación
public record ResourceOverview(Instant capturedAt, List<ResourceSnapshot> resources) {
    public ResourceOverview { resources = List.copyOf(resources); }
    public enum UnitStatus { AVAILABLE, IN_USE }
    public record UnitSnapshot(String id, UnitStatus status, ResourceOwner owner) {}
    public record WaitingOperation(ResourceOwner owner, Set<ResourceType> requestedResources,
                                   Set<ResourceType> unavailableResources) {
        public WaitingOperation {
            requestedResources = Set.copyOf(requestedResources);
            unavailableResources = Set.copyOf(unavailableResources);
        }
    }
    public record ResourceSnapshot(ResourceType type, int capacity, int available, int inUse,
                                   List<UnitSnapshot> units, List<WaitingOperation> waiting) {
        public ResourceSnapshot { units = List.copyOf(units); waiting = List.copyOf(waiting); }
    }
}
