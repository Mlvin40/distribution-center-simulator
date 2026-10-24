package com.redxela.api;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "redxela.test.inventory-isolation=true")
class InventoryApiTests {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> request(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Content-Type", "application/json").method(body == null ? "GET" : "POST", body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode read(HttpResponse<String> response) { return new JsonMapper().readTree(response.body()); }
    @Test void stockArrivalAutomaticallyMovesWaitingOrderIntoRealSchedulingQueue() throws Exception {
        var customer = read(request("/customers", "{\"name\":\"Stock HTTP\",\"serviceLevel\":2}"));
        var orderResponse = request("/orders", "{\"customerId\":\"" + customer.get("id").asString() + "\",\"lines\":[{\"productId\":\"PRD-005\",\"quantity\":2}]}");
        assertEquals(201, orderResponse.statusCode());
        var orderId = read(orderResponse).get("id").asString();
        assertEquals("WAITING_INVENTORY", read(orderResponse).get("status").asString());
        var waiting = read(request("/scheduling", null));
        assertEquals(0, waiting.get("eligible").size());
        assertEquals(1, waiting.get("waitingInventory").size());
        assertEquals(2, read(request("/inventory", null)).at("/waitingOrders/0/shortages/0/missing").asLong());
        String arrival = UUID.randomUUID().toString();
        String payload = "{\"arrivalId\":\"" + arrival + "\",\"productId\":\"PRD-005\",\"quantity\":2}";
        var received = request("/inventory/receipts", payload);
        assertEquals(200, received.statusCode(), received.body());
        long arrivalDeadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
        while (!read(request("/reception/" + arrival, null)).get("status").asString().equals("COMPLETED") && System.nanoTime() < arrivalDeadline) Thread.sleep(10);
        assertEquals(orderId, read(request("/reception/" + arrival, null)).at("/receipt/reactivatedOrders/0").asString());
        assertEquals("COMPLETED", read(request("/inventory/receipts", payload)).get("status").asString());
        assertEquals("READY", read(request("/orders/" + orderId, null)).get("status").asString());
        assertEquals("RESERVED", read(request("/inventory/reservations/" + orderId, null)).get("status").asString());
        var ready = read(request("/scheduling", null));
        assertEquals(1, ready.get("eligible").size());
        assertEquals(0, ready.get("waitingInventory").size());
        var inventory = read(request("/inventory", null));
        assertEquals(2, inventory.get("onHand").asLong());
        assertEquals(2, inventory.get("reserved").asLong());
        assertEquals(0, inventory.get("available").asLong());
        assertEquals(5, inventory.get("products").size());
        assertTrue(ready.get("metrics").get(0).get("completedOrders").asLong() == 0);
        assertEquals(409, request("/inventory/receipts", payload.replace("\"quantity\":2", "\"quantity\":3")).statusCode());
        assertEquals(409, request("/inventory/receipts", "{\"arrivalId\":\"" + UUID.randomUUID() + "\",\"productId\":\"PRD-001\",\"quantity\":601}").statusCode());
        assertEquals(400, request("/inventory/receipts", "{\"arrivalId\":\"" + UUID.randomUUID() + "\",\"productId\":\"PRD-001\",\"quantity\":1.2}").statusCode());
        assertEquals(400, request("/inventory/receipts", "{}").statusCode());
        assertEquals(404, request("/inventory/receipts", "{\"arrivalId\":\"" + UUID.randomUUID() + "\",\"productId\":\"missing\",\"quantity\":1}").statusCode());
        assertEquals(2, read(request("/inventory", null)).get("onHand").asLong());
        // El generador usa la misma admisión y puede reservar stock existente sin un navegador conectado
        request("/inventory/receipts", "{\"arrivalId\":\"" + UUID.randomUUID() + "\",\"productId\":\"PRD-001\",\"quantity\":100}");
        long stockDeadline=System.nanoTime()+java.time.Duration.ofSeconds(4).toNanos();
        while(read(request("/inventory",null)).get("onHand").asLong()<102 && System.nanoTime()<stockDeadline) Thread.sleep(10);
        request("/generator/start", "{\"intervalSimulatedSeconds\":0.1,\"seed\":42,\"minUnits\":1,\"maxUnits\":1}");
        try {
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
            while (read(request("/generator", null)).get("generatedOrders").asLong() < 30 && System.nanoTime() < deadline) Thread.sleep(10);
        } finally { request("/generator/stop", ""); }
        var all = read(request("/orders", null));
        boolean automaticReady = false;
        for (var order : all) if (order.get("source").asString().equals("AUTOMATIC") && order.get("status").asString().equals("READY")) automaticReady = true;
        assertTrue(automaticReady);
        assertTrue(read(request("/inventory", null)).get("reserved").asLong() > 2);
    }
}
