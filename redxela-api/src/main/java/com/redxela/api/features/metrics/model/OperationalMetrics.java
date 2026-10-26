package com.redxela.api.features.metrics.model;

import com.redxela.api.features.resource.model.ResourceType;
import java.util.Map;

// Medias de completados reales, separadas de los experimentos de comparación de políticas
public record OperationalMetrics(long received,long waiting,long processing,long completed,long failed,long cancelled,
        Double averageWaitSimulatedSeconds,Double averageProcessingSimulatedSeconds,long missedTargets,
        Map<ResourceType,Double> resourceUtilization,long storageOccupied,long storageCapacity) {
    public OperationalMetrics { resourceUtilization=Map.copyOf(resourceUtilization); }
}
