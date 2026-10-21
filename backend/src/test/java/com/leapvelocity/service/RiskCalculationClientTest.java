package com.leapvelocity.service;

import com.leapvelocity.dto.response.PortfolioRiskDto.VarResult;
import com.leapvelocity.exceptions.RiskCalculationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.leapvelocity.exceptions.RiskCalculationException.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class RiskCalculationClientTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final PortfolioReturnPreparation.PreparedInputs input = RiskParityFixtures.example(".95", 1);
    private HttpServer server;
    private ExecutorService executor;
    private String url;
    private int status = 200;
    private int delayMs;
    private int bodyDelayMs;
    private String raw;
    private Consumer<ObjectNode> mutate = ignored -> { };
    private volatile String receivedToken;
    private volatile String receivedBody;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/internal/v1/risk/calculate", exchange -> {
            receivedToken = exchange.getRequestHeaders().getFirst("X-Risk-Service-Token");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var id = json.readTree(receivedBody).path("requestId").asString();
            var core = new PortfolioRiskCalculator().calculate(input);
            var vars = new ArrayList<>(List.of(core.historicalVar()));
            vars.addAll(new NormalModelVarCalculator().calculate(input).values());
            var tree = (ObjectNode) json.valueToTree(new RiskCalculationContract.Response("1.0", id, core.metrics(), vars, List.of()));
            var scalars = List.of(core.metrics().annualisedVolatility(), core.metrics().maximumDrawdown(), core.metrics().beta());
            var names = List.of("annualisedVolatility", "maximumDrawdown", "beta");
            for (int index = 0; index < names.size(); index++) {
                ((ObjectNode) tree.at("/metrics/" + names.get(index))).put("value", scalars.get(index).value().toString());
            }
            for (int index = 0; index < vars.size(); index++) {
                VarResult var = vars.get(index);
                ((ObjectNode) tree.at("/valueAtRisk/" + index)).put("lossPercent", var.lossPercent().toString())
                        .put("lossAmount", var.lossAmount().toString());
            }
            mutate.accept(tree);
            byte[] bytes = (raw == null ? json.writeValueAsString(tree) : raw).getBytes(StandardCharsets.UTF_8);
            try {
                Thread.sleep(delayMs);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                Thread.sleep(bodyDelayMs);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    private RiskCalculationClient client() { return new RiskCalculationClient(url, "internal-test", 500, 1000); }

    @Test void successfulHttpUsesDecimalStringsAndServiceCredentials() {
        var result = client().calculate(input);
        assertEquals("1.0", result.schemaVersion());
        assertEquals("internal-test", receivedToken);
        var sent = json.readTree(receivedBody);
        assertTrue(sent.path("holdingsValue").isString());
        assertTrue(sent.at("/parameters/confidenceLevel").isString());
        assertTrue(sent.at("/portfolio/returns/0/value").isString());
        assertFalse(sent.has("accountId"));
        assertFalse(sent.has("cashBalance"));
    }

    @ParameterizedTest @ValueSource(ints = {401, 422, 500, 503, 302})
    void nonSuccessStatusIsAnOperationalFailure(int code) {
        status = code;
        assertEquals(UNAVAILABLE, assertThrows(RiskCalculationException.class, () -> client().calculate(input)).kind());
    }

    @Test void readTimeoutIsBoundedAndExplicit() {
        delayMs = 700;
        var client = new RiskCalculationClient(url, "internal-test", 500, 100);
        assertEquals(TIMEOUT, assertThrows(RiskCalculationException.class, () -> client.calculate(input)).kind());
    }

    @Test void connectionRefusalIsUnavailable() {
        server.stop(0);
        assertEquals(UNAVAILABLE, assertThrows(RiskCalculationException.class, () -> client().calculate(input)).kind());
    }

    @Test void responseBodyCannotStallAfterSuccessfulHeaders() {
        bodyDelayMs = 700;
        var client = new RiskCalculationClient(url, "internal-test", 500, 100);
        assertEquals(TIMEOUT, assertThrows(RiskCalculationException.class, () -> client.calculate(input)).kind());
    }

    @ParameterizedTest @ValueSource(strings = {"not-json", "null", "{}", "{\"schemaVersion\":\"1.0\"}"})
    void malformedAndMissingFieldsAreInvalidResponses(String body) {
        raw = body;
        assertEquals(INVALID_RESPONSE, assertThrows(RiskCalculationException.class, () -> client().calculate(input)).kind());
    }

    @ParameterizedTest @ValueSource(strings = {"version", "request", "method", "duplicates", "nan", "negative", "count", "dates", "unit", "null", "number", "status", "unknown"})
    void invalidReturnedValuesNeverReachPublicResponse(String corruption) {
        mutate = tree -> {
            var var = (ObjectNode) tree.at("/valueAtRisk/0");
            var scalar = (ObjectNode) tree.at("/metrics/annualisedVolatility");
            switch (corruption) {
                case "version" -> tree.put("schemaVersion", "2.0");
                case "request" -> tree.put("requestId", "different-snapshot");
                case "method" -> var.put("method", "MONTE_CARLO");
                case "duplicates" -> ((ObjectNode) tree.at("/valueAtRisk/1")).put("method", "HISTORICAL");
                case "nan" -> scalar.put("value", "NaN");
                case "negative" -> var.put("lossAmount", "-1.00");
                case "count" -> var.put("observations", 42);
                case "dates" -> var.put("from", "2020-01-01");
                case "unit" -> scalar.put("unit", "DIMENSIONLESS");
                case "null" -> scalar.putNull("value");
                case "number" -> scalar.put("value", 1.25);
                case "status" -> scalar.put("status", "EMPTY");
                case "unknown" -> tree.put("surprise", true);
            }
        };
        assertEquals(INVALID_RESPONSE, assertThrows(RiskCalculationException.class, () -> client().calculate(input)).kind());
    }

    @Test void activeTransactionIsRejectedBeforeHttp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> client().calculate(input)); }
        finally { TransactionSynchronizationManager.clear(); }
        assertNull(receivedBody);
    }

    @Test void configurationRequiresOriginTokenAndBoundedTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> new RiskCalculationClient(url, "", 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new RiskCalculationClient(url + "/wrong-path", "token", 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new RiskCalculationClient(url, "token", 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new RiskCalculationClient(url, "token", 100, 30001));
    }
}
