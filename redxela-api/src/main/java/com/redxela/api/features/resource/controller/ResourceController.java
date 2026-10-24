package com.redxela.api.features.resource.controller;

import com.redxela.api.features.resource.model.ResourceOverview;
import com.redxela.api.features.resource.service.ResourceManager;
import org.springframework.web.bind.annotation.*;

// Consultar nunca adquiere ni libera recursos desde el hilo HTTP
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceManager resources;
    public ResourceController(ResourceManager resources) { this.resources = resources; }
    @GetMapping public ResourceOverview snapshot() { return resources.snapshot(); }
}
