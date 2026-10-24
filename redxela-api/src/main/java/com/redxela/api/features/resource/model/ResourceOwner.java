package com.redxela.api.features.resource.model;

import java.util.UUID;

// Una operación tiene identidad propia aunque forme parte de un mismo pedido
public record ResourceOwner(UUID operationId, UUID orderId, OperationArea area, String description) {
    public ResourceOwner {
        if (operationId == null || area == null || description == null || description.isBlank())
            throw new IllegalArgumentException("La operación debe tener identidad, área y descripción");
    }
}
