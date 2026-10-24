package com.redxela.api;

import com.redxela.api.features.resource.model.*;
import com.redxela.api.features.resource.service.ResourceManager;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceApiTests {
    @LocalServerPort int port;
    @Autowired ResourceManager manager;
    private final HttpClient http = HttpClient.newHttpClient();
    private JsonNode get(String path) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return new JsonMapper().readTree(response.body());
    }
    @Test void exactLimitsOwnersAndWaitersAreVisibleOverHttp() throws Exception {
        var expected = Map.of("PACKING_STATION", 6, "LOADING_AREA", 2, "WAREHOUSE_WORKER", 10,
                "FORKLIFT", 3, "QUALITY_STATION", 4, "SCANNER", 1);
        var reception = new ResourceOwner(UUID.randomUUID(), null, OperationArea.RECEPTION, "Recepción de lote");
        var dispatch = new ResourceOwner(UUID.randomUUID(), UUID.randomUUID(), OperationArea.DISPATCH, "Despacho de pedido");
        try (var lease = manager.acquire(reception, Set.of(ResourceType.SCANNER), Duration.ZERO);
             var pool = Executors.newFixedThreadPool(1)) {
            var finished = new CountDownLatch(1);
            var waiting = pool.submit(() -> {
                try (var ignored = manager.acquire(dispatch, Set.of(ResourceType.SCANNER), Duration.ofSeconds(5))) {
                    fail("Recepción todavía posee el escáner");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                catch (TimeoutException ex) { fail(ex); }
                finally { finished.countDown(); }
            });
            try {
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                JsonNode state;
                do {
                    state = get("/api/resources").get("resources");
                    if (state.get(5).get("waiting").size() == 1) break;
                    Thread.sleep(5);
                } while (System.nanoTime() < end);
                assertEquals(6, state.size());
                for (var resource : state) {
                    int capacity = expected.get(resource.get("type").asString());
                    assertEquals(capacity, resource.get("capacity").asInt());
                    assertEquals(capacity, resource.get("units").size());
                    assertEquals(capacity, resource.get("available").asInt() + resource.get("inUse").asInt());
                }
                var scanner = state.get(5);
                assertEquals(0, scanner.get("available").asInt());
                assertEquals("IN_USE", scanner.get("units").get(0).get("status").asString());
                assertEquals(reception.operationId().toString(), scanner.at("/units/0/owner/operationId").asString());
                assertEquals(dispatch.operationId().toString(), scanner.at("/waiting/0/owner/operationId").asString());
                assertEquals("SCANNER", scanner.at("/waiting/0/unavailableResources/0").asString());
            } finally {
                waiting.cancel(true);
                assertTrue(finished.await(2, TimeUnit.SECONDS));
            }
        }
        var state = get("/api/resources").get("resources").get(5);
        assertEquals(1, state.get("available").asInt());
        assertTrue(state.get("waiting").isEmpty());
    }
    @Test void storageLayoutHasUniqueLocationsAndCoherentCapacity() throws Exception {
        var layout = get("/api/storage/layout");
        assertEquals(600, layout.get("totalCapacity").asInt());
        assertEquals(20, layout.get("locationCapacity").asInt());
        assertEquals(30, layout.get("locations").size());
        var ids = new HashSet<String>();
        long capacity = 0;
        for (var location : layout.get("locations")) {
            assertTrue(ids.add(location.get("id").asString()));
            assertTrue(location.get("aisle").asInt() >= 1 && location.get("aisle").asInt() <= 3);
            assertTrue(location.get("level").asInt() >= 1 && location.get("level").asInt() <= 2);
            capacity += location.get("capacity").asInt();
        }
        assertEquals(600, capacity);
        assertTrue(ids.contains("A01-L01-P01"));
        assertTrue(ids.contains("A03-L02-P05"));
    }
}
