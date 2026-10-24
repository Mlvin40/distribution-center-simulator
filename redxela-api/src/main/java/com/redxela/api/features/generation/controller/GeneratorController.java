package com.redxela.api.features.generation.controller;

import com.redxela.api.features.generation.model.GeneratorSettings;
import com.redxela.api.features.generation.service.OrderGenerator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

// Los comandos solo coordinan el ciclo de vida; las llegadas se ejecutan fuera del hilo HTTP
@RestController
@RequestMapping("/api/generator")
public class GeneratorController {
    private final OrderGenerator generator;
    public GeneratorController(OrderGenerator generator) { this.generator = generator; }
    @GetMapping public OrderGenerator.Status status() { return generator.status(); }
    @PostMapping("/start") public OrderGenerator.Status start(@Valid @RequestBody GeneratorSettings settings) { return generator.start(settings); }
    @PostMapping("/pause") public OrderGenerator.Status pause() { return generator.pause(); }
    @PostMapping("/resume") public OrderGenerator.Status resume() { return generator.resume(); }
    @PostMapping("/stop") public OrderGenerator.Status stop() { return generator.stop(); }
}
