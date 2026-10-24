package com.redxela.api.features.order.model;

import java.time.Instant;

// El historial conserva todas las entradas y salidas de espera, sin mezclar ambos relojes
public record OrderTransition(OrderStatus status, Instant realTime, double simulatedSeconds) {}
