package com.redxela.api.features.order.model;

public record OrderSelection(String productId, int quantity) {
    public OrderSelection {
        if (productId == null || productId.isBlank() || quantity <= 0)
            throw new IllegalArgumentException("Cada línea necesita producto y cantidad entera positiva");
    }
}
