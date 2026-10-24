package com.redxela.api.features.product.model;

import com.redxela.api.features.resource.model.ResourceType;
import java.util.Set;
import static com.redxela.api.features.resource.model.ResourceType.*;

// La mercancía define necesidades, independientemente de la prioridad contratada
public enum MerchandiseType {
    GENERAL("General", "Artículos de manejo convencional", Set.of(WAREHOUSE_WORKER, PACKING_STATION, SCANNER, LOADING_AREA)),
    FRAGILE("Frágil", "Requiere inspección y empaque protector", Set.of(WAREHOUSE_WORKER, QUALITY_STATION, PACKING_STATION, SCANNER, LOADING_AREA)),
    HEAVY("Pesado", "Traslado mecanizado y carga directa", Set.of(WAREHOUSE_WORKER, FORKLIFT, SCANNER, LOADING_AREA)),
    ELECTRONICS("Electrónica", "Inspección técnica y traslado protegido", Set.of(WAREHOUSE_WORKER, FORKLIFT, QUALITY_STATION, PACKING_STATION, SCANNER, LOADING_AREA)),
    BULKY("Voluminoso", "Traslado mecanizado con empaque de gran tamaño", Set.of(WAREHOUSE_WORKER, FORKLIFT, PACKING_STATION, SCANNER, LOADING_AREA));

    private final String displayName;
    private final String description;
    private final Set<ResourceType> requiredResources;
    MerchandiseType(String displayName, String description, Set<ResourceType> requiredResources) {
        this.displayName = displayName;
        this.description = description;
        this.requiredResources = Set.copyOf(requiredResources);
    }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Set<ResourceType> requiredResources() { return requiredResources; }
}
