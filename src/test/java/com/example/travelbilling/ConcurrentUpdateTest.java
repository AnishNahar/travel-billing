package com.example.travelbilling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentUpdateTest extends ApiTestBase {

    private static final int WRITERS = 8;

    @Test
    void overlappingPatchesNeverTearTheTaxAndFeeLists() throws Exception {
        String id = createSeed("flight");
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ApiClient.Response>> calls = new ArrayList<>();
            for (int writer = 0; writer < WRITERS; writer++) {
                Map<String, Object> body = patchFrom(writer);
                calls.add(pool.submit(() -> {
                    start.await();
                    return api.patch("/transactions/" + id, body);
                }));
            }
            start.countDown();
            for (Future<ApiClient.Response> call : calls) {
                assertThat(call.get().status()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }

        // Last write wins, but the stored row must be exactly one writer's request: never a mix of two.
        JsonNode stored = api.get("/transactions/" + id).body();
        int writer = Integer.parseInt(stored.at("/tax_lines/0/name").asText().split("-")[1]);
        assertMoney(stored.get("total"), String.valueOf(200 + writer));
        assertThat(stored.get("tax_lines")).hasSize(writer + 1);
        assertThat(stored.get("fee_lines")).hasSize(writer + 1);
        stored.get("tax_lines").forEach(line -> assertThat(line.get("name").asText()).startsWith("tax-" + writer + "-"));
        stored.get("fee_lines").forEach(line -> assertThat(line.get("name").asText()).startsWith("fee-" + writer + "-"));
    }

    /** Writer n sends total 200+n and n+1 tax and fee lines, all tagged with n. */
    private static Map<String, Object> patchFrom(int writer) {
        List<Map<String, Object>> taxes = new ArrayList<>();
        List<Map<String, Object>> fees = new ArrayList<>();
        for (int i = 0; i <= writer; i++) {
            taxes.add(Map.of("name", "tax-" + writer + "-" + i, "amount", BigDecimal.ONE));
            fees.add(Map.of("name", "fee-" + writer + "-" + i, "amount", BigDecimal.ONE));
        }
        return Map.of("total", BigDecimal.valueOf(200 + writer), "tax_lines", taxes, "fee_lines", fees);
    }
}
