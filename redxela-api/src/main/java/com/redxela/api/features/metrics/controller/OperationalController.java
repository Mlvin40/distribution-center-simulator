package com.redxela.api.features.metrics.controller;

import com.redxela.api.features.metrics.service.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// REST y SSE entregan el mismo contrato completo para recuperación sin reproducir eventos perdidos
@RestController
@RequestMapping("/api/operations")
public class OperationalController {
    private final OperationalSnapshotService snapshots;private final SimulationEvents events;
    public OperationalController(OperationalSnapshotService snapshots,SimulationEvents events){this.snapshots=snapshots;this.events=events;}
    @GetMapping public OperationalSnapshotService.Snapshot snapshot(){return snapshots.snapshot();}
    @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter subscribe(){return events.subscribe();}
}
