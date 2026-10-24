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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommerceApiTests {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();
    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Content-Type", "application/json").method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }
    @Test void realHttpCustomersCatalogAndMultiProductOrder() throws Exception {
        assertEquals("UP", read(request("GET", "/health", null)).get("status").asString());
        int[] targets = {0, 10, 30, 60, 120};
        var levels = read(request("GET", "/service-levels", null));
        assertEquals(5, levels.size());
        for (int level = 1; level <= 5; level++) {
            assertEquals(targets[level - 1], levels.get(level - 1).get("targetWaitSimulatedMinutes").asInt());
            assertEquals(201, request("POST", "/customers", "{\"name\":\"Cliente nuevo\",\"serviceLevel\":" + level + "}").statusCode());
        }
        var customerResponse = request("POST", "/customers", "{\"name\":\"Ana\",\"serviceLevel\":2}");
        String customer = read(customerResponse).get("id").asString();
        assertTrue(customerResponse.headers().firstValue("Location").orElseThrow().endsWith(customer));
        assertEquals(200, request("GET", "/customers/" + customer, null).statusCode());
        assertEquals(5, read(request("GET", "/products", null)).size());
        assertEquals(5, read(request("GET", "/merchandise-types", null)).size());
        var response = request("POST", "/orders", "{\"customerId\":\"" + customer + "\",\"lines\":[{\"productId\":\"PRD-002\",\"quantity\":2},{\"productId\":\"PRD-003\",\"quantity\":3}]}");
        assertEquals(201, response.statusCode(), response.body());
        var order = read(response);
        assertEquals(5, order.get("totalUnits").asInt());
        assertEquals(50, order.get("baseWorkSimulatedSeconds").asInt());
        assertEquals("PRIORITY", order.get("serviceLevel").asString());
        assertEquals("WAITING_INVENTORY", order.get("status").asString());
        assertEquals(6, order.get("requiredResources").size());
        assertEquals(2, order.get("history").size());
        assertTrue(order.get("processingStartedAt").isNull());
        assertEquals(order, read(request("GET", "/orders/" + order.get("id").asString(), null)));
        assertFalse(read(request("GET", "/orders", null)).isEmpty());
    }
    @Test void invalidInputsAreRejectedWithoutInternalDetails() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":\" \",\"serviceLevel\":1}",
                "{\"name\":\"A\",\"serviceLevel\":0}", "{\"name\":\"A\",\"serviceLevel\":6}",
                "{\"name\":\"A\",\"serviceLevel\":1.5}", "{broken"}) {
            var response = request("POST", "/customers", body);
            assertEquals(400, response.statusCode(), response.body());
            assertFalse(response.body().contains("trace"));
        }
        var customer = read(request("POST", "/customers", "{\"name\":\"Validación\",\"serviceLevel\":3}")).get("id").asString();
        for (String lines : new String[]{"[]", "[null]", "[{\"productId\":\"PRD-001\",\"quantity\":0}]",
                "[{\"productId\":\"PRD-001\",\"quantity\":-1}]", "[{\"productId\":\"PRD-001\",\"quantity\":1.2}]",
                "[{\"productId\":\"PRD-001\",\"quantity\":null}]",
                "[{\"productId\":\"PRD-001\",\"quantity\":1},{\"productId\":\"PRD-001\",\"quantity\":1}]"}) {
            var response = request("POST", "/orders", "{\"customerId\":\"" + customer + "\",\"lines\":" + lines + "}");
            assertEquals(400, response.statusCode(), response.body());
        }
        assertEquals(404, request("POST", "/orders", "{\"customerId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"productId\":\"PRD-001\",\"quantity\":1}]}").statusCode());
        assertEquals(404, request("POST", "/orders", "{\"customerId\":\"" + customer + "\",\"lines\":[{\"productId\":\"missing\",\"quantity\":1}]}").statusCode());
        assertEquals(404, request("GET", "/orders/" + UUID.randomUUID(), null).statusCode());
        assertEquals(400, request("GET", "/customers/bad-uuid", null).statusCode());
        assertEquals(404, request("GET", "/products/missing", null).statusCode());
    }
}
