package com.redxela.api;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingApiTests {
    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> request(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("Content-Type", "application/json").method(body == null ? "GET" : "POST", body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode read(HttpResponse<String> response) { return new JsonMapper().readTree(response.body()); }
    @Test void policyAndComparisonEndpointsPreserveLiveOrdersAndExposeTargets() throws Exception {
        assertEquals(3, read(request("/scheduling/policies", null)).size());
        var before = read(request("/orders", null));
        var response = request("/scheduling/policy", "{\"policy\":\"AGING\"}");
        assertEquals(200, response.statusCode());
        assertEquals("AGING", read(response).get("policy").asString());
        assertTrue(read(response).get("criterion").asString().contains("300"));
        var comparison = request("/scheduling/comparisons", "{\"seed\":42,\"orderCount\":20}");
        assertEquals(200, comparison.statusCode());
        assertEquals("ISOLATED_MODEL", read(comparison).get("mode").asString());
        assertEquals(3, read(comparison).get("results").size());
        assertEquals(before, read(request("/orders", null)));
        assertEquals(400, request("/scheduling/policy", "{\"policy\":null}").statusCode());
        assertEquals(400, request("/scheduling/comparisons", "{\"seed\":42,\"orderCount\":0}").statusCode());
        request("/scheduling/policy", "{\"policy\":\"STRICT_PRIORITY\"}");
    }
}
