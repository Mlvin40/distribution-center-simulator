package com.redxela.api.features.deadlock.controller;

import com.redxela.api.features.deadlock.service.DeadlockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

// La decisión HTTP solicita cancelación cooperativa; nunca libera recursos desde este hilo
@RestController
@RequestMapping("/api/deadlock")
public class DeadlockController {
    private final DeadlockService deadlock;
    public DeadlockController(DeadlockService deadlock){this.deadlock=deadlock;}
    @GetMapping public DeadlockService.Overview snapshot(){return deadlock.snapshot();}
    @PostMapping("/start") public DeadlockService.Overview start(){return deadlock.start();}
    @PostMapping("/resolve") public DeadlockService.Overview resolve(@Valid @RequestBody Resolution request){return deadlock.resolve(request.operationId());}
    public record Resolution(@NotNull UUID operationId){}
}
