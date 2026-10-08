package com.example.travelbilling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Required test 10
class PaginationTest extends ApiTestBase {

    @Test
    void transactionListIsBoundedAndCursorPaginated() {
        for (int i = 0; i < 55; i++) {
            create(seed("trip_fee").put("external_id", "fee-" + i));
        }

        JsonNode first = api.get("/transactions").body();
        assertThat(first.get("limit").asInt()).isEqualTo(50);
        assertThat(first.get("items")).hasSize(50);
        assertThat(first.get("next_cursor").isTextual()).isTrue();

        JsonNode second = api.get("/transactions?cursor=" + first.get("next_cursor").asText()).body();
        assertThat(second.get("items")).hasSize(5);
        assertThat(second.has("next_cursor")).isFalse();

        Set<String> seen = new HashSet<>();
        first.get("items").forEach(item -> seen.add(item.get("id").asText()));
        second.get("items").forEach(item -> seen.add(item.get("id").asText()));
        assertThat(seen).hasSize(55);
        assertThat(first.at("/items/0/external_id").asText()).isEqualTo("fee-54");
    }

    @Test
    void oversizedOrInvalidLimitsAreRejected() {
        assertThat(api.get("/transactions?limit=1000").status()).isEqualTo(400);
        assertThat(api.get("/transactions?limit=101").status()).isEqualTo(400);
        assertThat(api.get("/transactions?limit=0").status()).isEqualTo(400);
        assertThat(api.get("/transactions?limit=abc").status()).isEqualTo(400);
        assertThat(api.get("/transactions?cursor=garbage").status()).isEqualTo(400);
        assertThat(api.get("/transactions?limit=100").status()).isEqualTo(200);
        assertThat(api.get("/invoices?limit=1000").status()).isEqualTo(400);
    }

    @Test
    void invoiceListIsPaginatedToo() {
        String fee = createSeed("trip_fee");
        for (int i = 0; i < 3; i++) {
            invoice(List.of(fee));
        }

        JsonNode first = api.get("/invoices?limit=2").body();
        assertThat(first.get("items")).hasSize(2);
        assertMoney(first.at("/items/0/grand_total"), "25.00");
        assertThat(first.at("/items/0/line_count").asInt()).isEqualTo(1);

        JsonNode second = api.get("/invoices?limit=2&cursor=" + first.get("next_cursor").asText()).body();
        assertThat(second.get("items")).hasSize(1);
        assertThat(second.has("next_cursor")).isFalse();
    }
}
