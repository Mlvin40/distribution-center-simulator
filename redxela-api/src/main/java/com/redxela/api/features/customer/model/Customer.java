package com.redxela.api.features.customer.model;

import java.time.Instant;
import java.util.UUID;

// Cuenta inmutable sin credenciales: esta entrega no incluye autenticación
public record Customer(UUID id, String name, ServiceLevel serviceLevel, Instant createdAt) {}
