package com.redxela.api.features.scheduling;

import com.redxela.api.features.customer.model.ServiceLevel;
import com.redxela.api.features.product.service.ProductCatalog;
import com.redxela.api.features.resource.model.ResourceType;
import com.redxela.api.features.scheduling.model.*;
import com.redxela.api.features.scheduling.service.PolicyComparison;
import com.redxela.api.features.simulation.config.SimulationProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingPolicyTests {
    private SchedulingCandidate job(int id, int level, int units, double arrival) {
        return new SchedulingCandidate(new UUID(0, id), ServiceLevel.fromLevel(level), units, arrival, units * 10L, Set.of(ResourceType.SCANNER));
    }
    @Test void strictPriorityAgingAndSjfUseTheirOwnCriteria() {
        var economy = job(1, 5, 20, 0);
        var express = job(2, 1, 5, 1000);
        var small = job(3, 3, 1, 1000);
        var jobs = List.of(economy, express, small);
        assertEquals(express, jobs.stream().min(SchedulingPolicy.STRICT_PRIORITY.comparator(1200)).orElseThrow());
        assertEquals(economy, jobs.stream().min(SchedulingPolicy.AGING.comparator(1200)).orElseThrow());
        assertEquals(1, SchedulingPolicy.AGING.effectivePriority(economy, 1200));
        assertEquals(4, SchedulingPolicy.AGING.effectivePriority(economy, 300));
        assertEquals(small, jobs.stream().min(SchedulingPolicy.SHORTEST_JOB_FIRST.comparator(1200)).orElseThrow());
    }
    @Test void tiesUseArrivalThenUuidAndTargetsUseSimulatedMinutes() {
        var oldest = job(3, 3, 2, 0);
        var lowId = job(1, 3, 2, 10);
        var highId = job(2, 3, 2, 10);
        for (var policy : SchedulingPolicy.values()) {
            assertEquals(List.of(oldest, lowId, highId), List.of(highId, oldest, lowId).stream().sorted(policy.comparator(20)).toList());
        }
        int[] minutes = {0, 10, 30, 60, 120};
        for (int level = 1; level <= 5; level++) {
            var candidate = job(level, level, 1, 10);
            assertFalse(candidate.targetMissed(10 + minutes[level - 1] * 60));
            assertTrue(candidate.targetMissed(10.01 + minutes[level - 1] * 60));
        }
    }
    @Test void comparisonHasKnownMetricsAndProportionalResourceWork() {
        var settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
        var comparisons = new PolicyComparison(new ProductCatalog(), settings);
        var report = comparisons.compare(List.of(job(1, 5, 10, 0), job(2, 1, 4, 0), job(3, 3, 1, 0)));
        assertEquals("ISOLATED_MODEL", report.mode());
        var strict = report.results().get(0);
        var sjf = report.results().get(2);
        assertEquals(30, strict.averageWaitSimulatedSeconds());
        assertEquals(20, sjf.averageWaitSimulatedSeconds());
        assertEquals(50, strict.averageProcessingSimulatedSeconds());
        assertEquals(150, strict.makespanSimulatedSeconds());
        assertEquals(1, strict.resourceUtilization().get(ResourceType.SCANNER));
        assertEquals(3, strict.modeledCompletedOrders());
        var executions = strict.executions();
        var big = executions.stream().filter(e -> e.candidate().totalUnits() == 10).findFirst().orElseThrow();
        var small = executions.stream().filter(e -> e.candidate().totalUnits() == 1).findFirst().orElseThrow();
        assertEquals(10 * (small.finish() - small.start()), big.finish() - big.start());
        assertEquals(0, strict.missedTargetsByLevel().get(1));
    }
    @Test void seededComparisonsAreReproducibleAndRespectPhysicalCapacities() {
        var comparisons = new PolicyComparison(new ProductCatalog(), new SimulationProperties(60, 10, 3, 2, 5, 20));
        var first = comparisons.compare(42, 50);
        assertEquals(first, comparisons.compare(42, 50));
        assertNotEquals(first.workload(), comparisons.compare(43, 50).workload());
        for (var result : first.results()) {
            assertEquals(50, result.modeledCompletedOrders());
            for (var type : ResourceType.values()) {
                assertTrue(result.resourceUtilization().get(type) >= 0 && result.resourceUtilization().get(type) <= 1);
                for (var execution : result.executions()) {
                    long concurrent = result.executions().stream().filter(other -> other.start() <= execution.start() && other.finish() > execution.start()
                            && other.candidate().requiredResources().contains(type)).count();
                    assertTrue(concurrent <= type.capacity());
                }
            }
        }
    }
}
