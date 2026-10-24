package com.redxela.api.features.product.model;

// Identificación estable para que las recepciones y pedidos compartan el mismo catálogo
public record Product(String id, String name, String description, MerchandiseType merchandiseType) {}
