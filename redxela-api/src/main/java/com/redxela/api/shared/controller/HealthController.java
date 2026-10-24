package com.redxela.api.shared.controller;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Comprobación de conectividad independiente del estado del motor
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "REDXela", Instant.now());
    }

    public record HealthResponse(String status, String application, Instant timestamp) {}
}
