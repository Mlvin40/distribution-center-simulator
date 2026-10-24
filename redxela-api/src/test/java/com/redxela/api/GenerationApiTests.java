package com.redxela.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationApiTests {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> request(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json").method(body == null ? "GET" : "POST", body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode read(HttpResponse<String> response) { return new JsonMapper().readTree(response.body()); }
    @Test void generatorHttpIsResponsiveAndAcceptsManualOrdersWhileRunning() throws Exception {
        assertEquals(200, request("/generator/start", "{\"intervalSimulatedSeconds\":0.1,\"seed\":42,\"minUnits\":1,\"maxUnits\":3}").statusCode());
        try {
            var customer = read(request("/customers", "{\"name\":\"HTTP concurrente\",\"serviceLevel\":1}"));
            var manual = request("/orders", "{\"customerId\":\"" + customer.get("id").asString() + "\",\"lines\":[{\"productId\":\"PRD-001\",\"quantity\":2}]}");
            assertEquals(201, manual.statusCode());
            assertEquals("MANUAL", read(manual).get("source").asString());
            assertEquals(200, request("/health", null).statusCode());
            long end = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (read(request("/generator", null)).get("generatedOrders").asLong() < 5 && System.nanoTime() < end) Thread.sleep(10);
            var paused = read(request("/generator/pause", ""));
            assertEquals("PAUSED", paused.get("state").asString());
            assertTrue(paused.get("generatedOrders").asLong() >= 5);
            Thread.sleep(60);
            assertEquals(paused.get("generatedOrders"), read(request("/generator", null)).get("generatedOrders"));
            assertEquals(200, request("/generator/resume", "").statusCode());
            assertEquals(200, request("/orders?limit=5", null).statusCode());
            assertTrue(read(request("/orders?limit=5", null)).size() <= 5);
            assertEquals(400, request("/orders?limit=0", null).statusCode());
        } finally { request("/generator/stop", ""); }
        assertEquals("STOPPED", read(request("/generator", null)).get("state").asString());
        assertEquals(409, request("/generator/resume", "").statusCode());
    }
}
