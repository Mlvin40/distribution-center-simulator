package com.redxela.api.features.scheduling.service;

import com.redxela.api.features.customer.model.ServiceLevel;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.ResourceType;
import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.simulation.config.SimulationProperties;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

// Experimento analítico determinista, aislado de pedidos, stock y recursos del sistema vivo
@Service
public class PolicyComparison {
    private final ProductCatalog catalog;
    private final SimulationProperties settings;
    public PolicyComparison(ProductCatalog catalog, SimulationProperties settings) { this.catalog = catalog; this.settings = settings; }
    public Report compare(long seed, int orderCount) {
        if (orderCount < 1 || orderCount > 200) throw new IllegalArgumentException("El experimento admite entre 1 y 200 entradas");
        var random = new Random(seed);
        var workload = new ArrayList<SchedulingCandidate>();
        for (int index = 0; index < orderCount; index++) {
            long units = 1 + random.nextInt(20);
            var type = catalog.list().get(random.nextInt(catalog.list().size())).merchandiseType();
            workload.add(new SchedulingCandidate(UUID.nameUUIDFromBytes((seed + ":" + index).getBytes(StandardCharsets.UTF_8)),
                    ServiceLevel.fromLevel(index % 5 + 1), units, index / 5 * 30, settings.baseWorkSeconds(units), type.requiredResources()));
        }
        return compare(workload);
    }
    public Report compare(List<SchedulingCandidate> workload) {
        workload = List.copyOf(workload);
        if (workload.isEmpty() || workload.size() > 200 || workload.stream().map(SchedulingCandidate::id).distinct().count() != workload.size())
            throw new IllegalArgumentException("La carga debe contener entre 1 y 200 candidatos únicos");
        var results = new ArrayList<Result>();
        for (var policy : SchedulingPolicy.values()) results.add(run(policy, workload));
        return new Report("ISOLATED_MODEL", "Existencias suficientes; recursos retenidos como conjunto durante el trabajo base; no modifica pedidos reales", workload, results);
    }
    private Result run(SchedulingPolicy policy, List<SchedulingCandidate> workload) {
        var pending = new ArrayList<>(workload);
        var running = new PriorityQueue<Execution>(Comparator.comparingDouble(Execution::finish).thenComparing(execution -> execution.candidate().id()));
        var available = new EnumMap<ResourceType, Integer>(ResourceType.class);
        var busy = new EnumMap<ResourceType, Double>(ResourceType.class);
        for (var type : ResourceType.values()) { available.put(type, type.capacity()); busy.put(type, 0.0); }
        double origin = pending.stream().mapToDouble(SchedulingCandidate::arrivalSimulatedSeconds).min().orElseThrow();
        double now = origin;
        var executions = new ArrayList<Execution>();
        while (!pending.isEmpty() || !running.isEmpty()) {
            while (!running.isEmpty() && running.peek().finish() <= now) {
                var finished = running.remove();
                for (var type : finished.candidate().requiredResources()) available.merge(type, 1, Integer::sum);
            }
            double instant = now;
            var ready = pending.stream().filter(candidate -> candidate.arrivalSimulatedSeconds() <= instant).sorted(policy.comparator(now)).toList();
            for (var candidate : ready) {
                if (candidate.requiredResources().stream().anyMatch(type -> available.get(type) == 0)) continue;
                for (var type : candidate.requiredResources()) {
                    available.merge(type, -1, Integer::sum);
                    busy.merge(type, (double) candidate.workSimulatedSeconds(), Double::sum);
                }
                var execution = new Execution(candidate, now, now + candidate.workSimulatedSeconds());
                executions.add(execution); running.add(execution); pending.remove(candidate);
            }
            double arrival = pending.stream().mapToDouble(SchedulingCandidate::arrivalSimulatedSeconds).filter(time -> time > instant).min().orElse(Double.POSITIVE_INFINITY);
            double finish = running.isEmpty() ? Double.POSITIVE_INFINITY : running.peek().finish();
            double next = Math.min(arrival, finish);
            if (Double.isInfinite(next)) {
                if (!pending.isEmpty()) throw new IllegalStateException("Carga sin posibilidad de progreso");
                break;
            }
            now = next;
        }
        double horizon = executions.stream().mapToDouble(Execution::finish).max().orElseThrow() - origin;
        var utilization = new EnumMap<ResourceType, Double>(ResourceType.class);
        for (var type : ResourceType.values()) utilization.put(type, busy.get(type) / (horizon * type.capacity()));
        var missedByLevel = new TreeMap<Integer, Long>();
        for (var level : ServiceLevel.values()) missedByLevel.put(level.level(), executions.stream()
                .filter(e -> e.candidate().serviceLevel() == level && e.candidate().targetMissed(e.start())).count());
        return new Result(policy, executions.size(), executions.stream().mapToDouble(e -> e.candidate().waitSeconds(e.start())).average().orElseThrow(),
                executions.stream().mapToDouble(e -> e.finish() - e.start()).average().orElseThrow(), horizon,
                missedByLevel, utilization, executions);
    }
    public record Execution(SchedulingCandidate candidate, double start, double finish) {}
    public record Result(SchedulingPolicy policy, int modeledCompletedOrders, double averageWaitSimulatedSeconds,
                         double averageProcessingSimulatedSeconds, double makespanSimulatedSeconds,
                         Map<Integer, Long> missedTargetsByLevel, Map<ResourceType, Double> resourceUtilization, List<Execution> executions) {
        public Result { missedTargetsByLevel = Map.copyOf(missedTargetsByLevel); resourceUtilization = Map.copyOf(resourceUtilization); executions = List.copyOf(executions); }
    }
    public record Report(String mode, String assumptions, List<SchedulingCandidate> workload, List<Result> results) {
        public Report { workload = List.copyOf(workload); results = List.copyOf(results); }
    }
}
