package com.redxela.api.features.simulation;

import com.redxela.api.features.order.model.OrderStatus;
import com.redxela.api.features.product.model.MerchandiseType;
import com.redxela.api.features.simulation.config.SimulationProperties;
import com.redxela.api.features.simulation.service.SimulationClock;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationDesignTests {
    @Test void monotonicTimeAndProportionalWork() {
        var nanos = new AtomicLong(100);
        var clock = new SimulationClock(60, nanos::get, Instant.EPOCH);
        nanos.addAndGet(2_000_000_000L);
        assertEquals(120, clock.elapsedSeconds());
        assertEquals(Instant.EPOCH, clock.realOrigin());
        var settings = new SimulationProperties(60, 10, 3, 2, 5, 20);
        assertEquals(600, settings.storageCapacity());
        assertEquals(10 * settings.baseWorkSeconds(2), settings.baseWorkSeconds(20));
        assertThrows(IllegalArgumentException.class, () -> settings.baseWorkSeconds(0));
    }
    @Test void configuredPropertiesAreValidatedBeforeStarting() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new SimulationProperties(60, 10, 3, 2, 5, 20)).isEmpty());
            assertFalse(validator.validate(new SimulationProperties(0, 10, 3, 2, 5, 20)).isEmpty());
            assertFalse(validator.validate(new SimulationProperties(60, 0, 3, 2, 5, 20)).isEmpty());
            assertFalse(validator.validate(new SimulationProperties(60, 10, 0, 2, 5, 20)).isEmpty());
        }
    }
    @Test void fiveIndependentResourceCombinations() {
        assertEquals(5, MerchandiseType.values().length);
        assertEquals(5, Arrays.stream(MerchandiseType.values()).map(MerchandiseType::requiredResources).distinct().count());
    }
    @Test void terminalStatesAndDispatchCannotBeSkipped() {
        assertFalse(OrderStatus.RECEIVED.canTransitionTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.READY));
        assertTrue(OrderStatus.LOADING.canTransitionTo(OrderStatus.COMPLETED));
        assertTrue(OrderStatus.RECEIVED.canTransitionTo(OrderStatus.WAITING_INVENTORY));
    }
}
