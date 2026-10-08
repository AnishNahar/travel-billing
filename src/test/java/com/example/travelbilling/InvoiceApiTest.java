package com.example.travelbilling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceApiTest extends ApiTestBase {

    // Required test 3
    @Test
    void tripInvoiceMatchesTheExpectedFixture() {
        Map<String, String> ids = createTrip();

        JsonNode invoice = invoice(new ArrayList<>(ids.values()));

        JsonNode expected = expectedInvoice.get("totals");
        assertThat(invoice.get("currency").asText()).isEqualTo(expectedInvoice.get("currency").asText());
        assertMoney(invoice.at("/totals/grand_total"), "685.70");
        // Every key of expected-invoice.json "totals" (flight, hotel, rail, trip_fee, agent_call_fee, grand_total).
        expected.fieldNames().forEachRemaining(key -> assertThat(money(invoice.get("totals").get(key)))
                .as("totals." + key).isEqualByComparingTo(money(expected.get(key))));

        JsonNode lines = invoice.get("lines");
        assertThat(lines.size()).isGreaterThanOrEqualTo(expectedInvoice.get("line_count_minimum").asInt());
        for (int i = 0; i < TRIP.size(); i++) {
            String seedName = TRIP.get(i);
            JsonNode line = lines.get(i);
            assertThat(line.get("transaction_id").asText()).isEqualTo(ids.get(seedName));
            assertThat(money(line.get("total"))).as(seedName).isEqualByComparingTo(money(expected.get(seedName)));
            assertThat(line.at("/snapshot/total")).isEqualTo(line.get("total"));
        }

        List<String> types = new ArrayList<>();
        List<String> feeKinds = new ArrayList<>();
        lines.forEach(line -> {
            types.add(line.get("type").asText());
            if (line.at("/snapshot/metadata/fee_kind").isTextual()) {
                feeKinds.add(line.at("/snapshot/metadata/fee_kind").asText());
            }
        });
        expectedInvoice.get("transaction_types_included").forEach(t -> assertThat(types).contains(t.asText()));
        expectedInvoice.get("navan_fee_kinds_included").forEach(k -> assertThat(feeKinds).contains(k.asText()));

        assertThat(lines.get(0).at("/snapshot/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(lines.get(1).at("/snapshot/metadata/address/city").asText()).isEqualTo("London");
        assertThat(lines.get(2).at("/snapshot/metadata/legs")).hasSize(2);

        JsonNode taxRollup = invoice.get("tax_rollup");
        assertThat(taxRollup).hasSize(2);
        assertThat(taxRollup.at("/0/name").asText()).isEqualTo("Airport tax");
        assertThat(taxRollup.at("/0/rate").isMissingNode()).isTrue();
        assertMoney(taxRollup.at("/0/amount"), "15.00");
        assertThat(taxRollup.at("/1/name").asText()).isEqualTo("VAT");
        assertMoney(taxRollup.at("/1/rate"), "0.07");
        assertMoney(taxRollup.at("/1/amount"), "21.70");

        JsonNode feeRollup = invoice.get("fee_rollup");
        assertThat(feeRollup).hasSize(2);
        assertThat(feeRollup.at("/0/name").asText()).isEqualTo("YQ");
        assertMoney(feeRollup.at("/0/amount"), "25.00");
        assertThat(feeRollup.at("/1/name").asText()).isEqualTo("Booking fee");
        assertMoney(feeRollup.at("/1/amount"), "4.00");

        assertMoney(invoice.at("/totals/tax_total"), "36.70");
        assertMoney(invoice.at("/totals/fee_total"), "29.00");

        assertThat(api.get("/invoices/" + invoice.get("id").asText()).body()).isEqualTo(invoice);
    }

    @Test
    void singleTransactionIdShapeIsSupported() {
        String flight = createSeed("flight");

        ApiClient.Response response = api.post("/invoices", Map.of("transaction_id", flight));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("lines")).hasSize(1);
        assertMoney(response.body().at("/totals/grand_total"), "220.00");
    }

    // Required test 4
    @Test
    void invoiceIsAFrozenSnapshotOfTheTransactions() {
        Map<String, String> ids = createTrip();
        String invoiceId = invoice(new ArrayList<>(ids.values())).get("id").asText();
        long flightRowId = Long.parseLong(ids.get("flight").substring("txn_".length()));

        // The API refuses to edit an invoiced transaction (see the 409 test), so change the stored row directly:
        // if the invoice were a live join it would now show these values.
        jdbc.update("UPDATE transactions SET total = 999.99 WHERE id = ?", flightRowId);
        jdbc.update("UPDATE transaction_tax_lines SET amount = 500.00 WHERE transaction_id = ?", flightRowId);
        jdbc.update("UPDATE flight_coupons SET flight_number = 'XX0000' WHERE transaction_id = ?", flightRowId);
        assertMoney(api.get("/transactions/" + ids.get("flight")).body().get("total"), "999.99");

        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        JsonNode flightLine = invoice.get("lines").get(0);
        assertMoney(invoice.at("/totals/flight"), "220.00");
        assertMoney(flightLine.get("total"), "220.00");
        assertMoney(flightLine.at("/snapshot/total"), "220.00");
        assertMoney(flightLine.at("/snapshot/tax_lines/0/amount"), "15.00");
        assertThat(flightLine.at("/snapshot/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertMoney(invoice.at("/tax_rollup/0/amount"), "15.00");
        assertMoney(invoice.at("/totals/grand_total"), "685.70");
    }

    // Required test 5
    @Test
    void mixedCurrenciesAreRejected() {
        String eurFlight = createSeed("flight");
        ObjectNode usdHotel = seed("hotel").put("currency", "USD");
        String usdHotelId = create(usdHotel);

        ApiClient.Response response = api.post("/invoices", Map.of("transaction_ids", List.of(eurFlight, usdHotelId)));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("error").asText()).isEqualTo("mixed_currency");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class)).isZero();
    }

    // Required test 7
    @Test
    void patchingAnInvoicedTransactionIs409AndChangesNothing() {
        Map<String, String> ids = createTrip();
        String invoiceId = invoice(new ArrayList<>(ids.values())).get("id").asText();

        ApiClient.Response response = api.patch("/transactions/" + ids.get("flight"), Map.of("total", 300.00));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("error").asText()).isEqualTo("transaction_invoiced");
        assertMoney(api.get("/transactions/" + ids.get("flight")).body().get("total"), "220.00");
        JsonNode invoice = api.get("/invoices/" + invoiceId).body();
        assertMoney(invoice.at("/totals/grand_total"), "685.70");
        assertMoney(invoice.at("/totals/flight"), "220.00");
    }

    // Required test 8
    @Test
    void deletingAnInvoicedTransactionIs409AndTheRowStays() {
        Map<String, String> ids = createTrip();
        invoice(new ArrayList<>(ids.values()));

        ApiClient.Response response = api.delete("/transactions/" + ids.get("hotel"));

        assertThat(response.status()).isEqualTo(409);
        ApiClient.Response stillThere = api.get("/transactions/" + ids.get("hotel"));
        assertThat(stillThere.status()).isEqualTo(200);
        assertThat(stillThere.body().at("/metadata/name").asText()).isEqualTo("Hotel Mitte London");
    }

    // Required test 9
    @Test
    void deletingAnInvoiceKeepsItsTransactions() {
        Map<String, String> ids = createTrip();
        String invoiceId = invoice(new ArrayList<>(ids.values())).get("id").asText();

        assertThat(api.delete("/invoices/" + invoiceId).status()).isEqualTo(204);

        assertThat(api.get("/invoices/" + invoiceId).status()).isEqualTo(404);
        ids.values().forEach(id -> assertThat(api.get("/transactions/" + id).status()).isEqualTo(200));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice_lines", Integer.class)).isZero();
        assertThat(api.patch("/transactions/" + ids.get("flight"), Map.of("total", 230.00)).status()).isEqualTo(200);
        assertThat(api.delete("/invoices/" + invoiceId).status()).isEqualTo(404);
    }

    @Test
    void theSameTransactionTwiceOnOneInvoiceIs409() {
        String flight = createSeed("flight");

        ApiClient.Response response = api.post("/invoices", Map.of("transaction_ids", List.of(flight, flight)));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("error").asText()).isEqualTo("duplicate_transaction_id");
    }

    @Test
    void missingTransactionIs404() {
        String flight = createSeed("flight");

        ApiClient.Response response = api.post("/invoices", Map.of("transaction_ids", List.of(flight, "txn_424242")));

        assertThat(response.status()).isEqualTo(404);
        assertThat(api.get("/invoices/inv_424242").status()).isEqualTo(404);
    }

    @Test
    void badInvoiceBodiesAre400() {
        assertThat(api.post("/invoices", Map.of()).status()).isEqualTo(400);
        assertThat(api.post("/invoices", Map.of("transaction_ids", List.of())).status()).isEqualTo(400);
        assertThat(api.post("/invoices", Map.of("transaction_id", "txn_1", "transaction_ids", List.of("txn_1"))).status())
                .isEqualTo(400);
    }

    @Test
    void patchInvoiceRebuildsTheSnapshotAtomically() {
        Map<String, String> ids = createTrip();
        String invoiceId = invoice(List.of(ids.get("flight"))).get("id").asText();

        ApiClient.Response patched = api.patch("/invoices/" + invoiceId,
                Map.of("transaction_ids", List.of(ids.get("hotel"), ids.get("rail"))));

        assertThat(patched.status()).isEqualTo(200);
        assertThat(patched.body().get("id").asText()).isEqualTo(invoiceId);
        assertThat(patched.body().get("lines")).hasSize(2);
        assertMoney(patched.body().at("/totals/grand_total"), "425.70");
        assertThat(patched.body().get("totals").has("flight")).isFalse();
        assertThat(patched.body().at("/tax_rollup/0/name").asText()).isEqualTo("VAT");
        assertThat(patched.body().at("/fee_rollup/0/name").asText()).isEqualTo("Booking fee");

        // The flight left the invoice, so it is editable again.
        assertThat(api.patch("/transactions/" + ids.get("flight"), Map.of("total", 230.00)).status()).isEqualTo(200);

        String usdFee = create(seed("trip_fee").put("external_id", "usd-fee").put("currency", "USD"));
        ApiClient.Response mixed = api.patch("/invoices/" + invoiceId,
                Map.of("transaction_ids", List.of(ids.get("hotel"), usdFee)));
        assertThat(mixed.status()).isEqualTo(409);
        ApiClient.Response missing = api.patch("/invoices/" + invoiceId, Map.of("transaction_ids", List.of("txn_424242")));
        assertThat(missing.status()).isEqualTo(404);

        assertThat(api.get("/invoices/" + invoiceId).body()).isEqualTo(patched.body());
        assertThat(api.patch("/invoices/inv_424242", Map.of("transaction_id", ids.get("hotel"))).status()).isEqualTo(404);
    }
}
