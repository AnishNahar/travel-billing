package com.example.travelbilling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the real app on a random port against a file-backed H2 database and wipes it before each test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class ApiTestBase {

    protected static final List<String> TRIP = List.of("flight", "hotel", "rail", "trip_fee", "agent_call_fee");

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper mapper;

    protected ApiClient api;
    protected JsonNode seeds;
    protected JsonNode expectedInvoice;

    @BeforeEach
    void resetDatabaseAndLoadFixtures() throws IOException {
        jdbc.update("DELETE FROM invoices");
        jdbc.update("DELETE FROM transactions");
        api = new ApiClient("http://localhost:" + port, mapper);
        seeds = mapper.readTree(Path.of("fixtures/task-b/seed-payloads.json").toFile());
        expectedInvoice = mapper.readTree(Path.of("fixtures/task-b/expected-invoice.json").toFile());
    }

    protected ObjectNode seed(String name) {
        return seeds.get(name).deepCopy();
    }

    protected String create(JsonNode body) {
        ApiClient.Response response = api.post("/transactions", body);
        assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
        return response.body().get("id").asText();
    }

    protected String createSeed(String name) {
        return create(seed(name));
    }

    /** Creates the five trip seeds; returns seed name -> transaction id, in trip order. */
    protected Map<String, String> createTrip() {
        Map<String, String> ids = new LinkedHashMap<>();
        TRIP.forEach(name -> ids.put(name, createSeed(name)));
        return ids;
    }

    protected JsonNode invoice(List<String> transactionIds) {
        ApiClient.Response response = api.post("/invoices", Map.of("transaction_ids", transactionIds));
        assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
        return response.body();
    }

    protected int rowsWithExternalId(String externalId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class, externalId);
    }

    protected static BigDecimal money(JsonNode node) {
        return node.decimalValue();
    }

    protected static void assertMoney(JsonNode actual, String expected) {
        assertThat(actual.decimalValue()).isEqualByComparingTo(expected);
    }
}
