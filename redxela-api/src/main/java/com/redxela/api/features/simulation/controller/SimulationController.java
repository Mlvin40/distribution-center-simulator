package com.redxela.api.features.simulation.controller;

import com.redxela.api.features.simulation.service.ProcessingEngine;
import org.springframework.web.bind.annotation.*;

// Los comandos activan trabajadores; el hilo HTTP nunca ejecuta trabajo de pedidos
@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    private final ProcessingEngine engine;
    public SimulationController(ProcessingEngine engine){this.engine=engine;}
    @GetMapping public ProcessingEngine.Status status(){return engine.status();}
    @PostMapping("/start") public ProcessingEngine.Status start(){return engine.start();}
    @PostMapping("/stop") public ProcessingEngine.Status stop(){return engine.stop();}
    @GetMapping("/active") public Object active(){return engine.snapshot();}
    @GetMapping("/events") public Object events(){return engine.events();}
}
