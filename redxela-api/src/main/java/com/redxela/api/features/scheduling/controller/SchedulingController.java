package com.redxela.api.features.scheduling.controller;

import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.scheduling.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

// Consultar y comparar no despacha pedidos; claimNext queda reservado a trabajadores Java
@RestController
@RequestMapping("/api/scheduling")
public class SchedulingController {
    private final OrderScheduler scheduler;
    private final PolicyComparison comparisons;
    public SchedulingController(OrderScheduler scheduler, PolicyComparison comparisons) { this.scheduler = scheduler; this.comparisons = comparisons; }
    @GetMapping public SchedulingOverview snapshot() { return scheduler.snapshot(); }
    @PostMapping("/policy") public SchedulingOverview policy(@Valid @RequestBody PolicyRequest request) { return scheduler.changePolicy(request.policy()); }
    @GetMapping("/policies") public List<PolicyDescription> policies() {
        return Arrays.stream(SchedulingPolicy.values()).map(policy -> new PolicyDescription(policy, policy.displayName(), policy.criterion())).toList();
    }
    @PostMapping("/comparisons") public PolicyComparison.Report compare(@Valid @RequestBody ComparisonRequest request) { return comparisons.compare(request.seed(), request.orderCount()); }
    public record PolicyRequest(@NotNull SchedulingPolicy policy) {}
    public record PolicyDescription(SchedulingPolicy code, String name, String criterion) {}
    public record ComparisonRequest(long seed, @Min(1) @Max(200) int orderCount) {}
}
