package com.example.travelbilling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionApiTest extends ApiTestBase {

    // Required test 1
    @Test
    void createsEveryTypeFromTheSeedsAndGetReturnsTheTypedStructure() {
        Map<String, String> ids = createTrip();

        JsonNode flight = api.get("/transactions/" + ids.get("flight")).body();
        assertThat(flight.get("type").asText()).isEqualTo("flight");
        assertThat(flight.at("/metadata/origin").asText()).isEqualTo("TXL");
        assertThat(flight.at("/metadata/destination").asText()).isEqualTo("LHR");
        assertMoney(flight.at("/metadata/fare"), "180.00");
        assertThat(flight.at("/metadata/coupons")).hasSize(1);
        assertThat(flight.at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
        assertThat(flight.at("/metadata/coupons/0/carrier").asText()).isEqualTo("BA");
        assertThat(flight.at("/metadata/coupons/0/departure").asText()).isEqualTo("2026-03-11T07:00:00Z");
        assertThat(flight.at("/tax_lines/0/name").asText()).isEqualTo("Airport tax");
        assertMoney(flight.at("/tax_lines/0/amount"), "15.00");
        assertThat(flight.at("/fee_lines/0/name").asText()).isEqualTo("YQ");
        assertMoney(flight.get("total"), "220.00");

        JsonNode hotel = api.get("/transactions/" + ids.get("hotel")).body();
        assertThat(hotel.at("/metadata/name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(hotel.at("/metadata/address/street").asText()).isEqualTo("12 Kingsway");
        assertThat(hotel.at("/metadata/address/city").asText()).isEqualTo("London");
        assertThat(hotel.at("/metadata/address/country").asText()).isEqualTo("GB");
        assertThat(hotel.at("/metadata/check_in").asText()).isEqualTo("2026-03-11");
        assertThat(hotel.at("/metadata/nights").asInt()).isEqualTo(2);
        assertThat(hotel.at("/metadata/extras/0/name").asText()).isEqualTo("Breakfast");
        assertMoney(hotel.at("/tax_lines/0/rate"), "0.07");
        assertThat(hotel.get("fee_lines")).isEmpty();

        JsonNode rail = api.get("/transactions/" + ids.get("rail")).body();
        assertThat(rail.at("/metadata/legs")).hasSize(2);
        assertThat(rail.at("/metadata/legs/0/destination").asText()).isEqualTo("Lille Europe");
        assertThat(rail.at("/metadata/legs/1/origin").asText()).isEqualTo("Lille Europe");
        assertThat(rail.at("/metadata/legs/1/train_number").asText()).isEqualTo("EST9122");
        assertThat(rail.at("/metadata/legs/1/class").asText()).isEqualTo("standard");

        JsonNode tripFee = api.get("/transactions/" + ids.get("trip_fee")).body();
        assertThat(tripFee.get("type").asText()).isEqualTo("navan_fee");
        assertThat(tripFee.at("/metadata/fee_kind").asText()).isEqualTo("trip_fee");
        assertThat(tripFee.at("/metadata/related_external_id").asText()).isEqualTo("flight-TXL-LHR-2026-03-11");

        JsonNode agentFee = api.get("/transactions/" + ids.get("agent_call_fee")).body();
        assertThat(agentFee.at("/metadata/fee_kind").asText()).isEqualTo("agent_call_fee");
        assertThat(agentFee.at("/metadata/description").asText()).isEqualTo("Agent fee for support call");
    }

    // Required test 2
    @Test
    void duplicateExternalIdDoesNotInsertASecondRow() {
        String id = createSeed("flight");

        ApiClient.Response duplicate = api.post("/transactions", seed("duplicate_flight"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().get("error").asText()).isEqualTo("duplicate_external_id");
        assertThat(duplicate.body().get("existing_id").asText()).isEqualTo(id);

        ApiClient.Response sameBodyAgain = api.post("/transactions", seed("flight"));
        assertThat(sameBodyAgain.status()).isEqualTo(409);
        assertThat(sameBodyAgain.body().get("existing_id").asText()).isEqualTo(id);

        assertThat(rowsWithExternalId("flight-TXL-LHR-2026-03-11")).isEqualTo(1);
        assertThat(api.get("/transactions/" + id).body().at("/metadata/coupons")).hasSize(1);
    }

    @Test
    void createReturnsTheStoredRecordWithAServerId() {
        ApiClient.Response response = api.post("/transactions", seed("hotel"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("id").asText()).startsWith("txn_");
        assertThat(response.body()).isEqualTo(api.get("/transactions/" + response.body().get("id").asText()).body());
    }

    @Test
    void unknownTypeIsRejected() {
        ObjectNode body = seed("flight").put("type", "car_rental");

        ApiClient.Response response = api.post("/transactions", body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().at("/details/0/field").asText()).isEqualTo("type");
        assertThat(rowsWithExternalId("flight-TXL-LHR-2026-03-11")).isZero();
    }

    @Test
    void unknownNavanFeeKindIsRejected() {
        ObjectNode body = seed("trip_fee");
        ((ObjectNode) body.get("metadata")).put("fee_kind", "lounge_fee");

        ApiClient.Response response = api.post("/transactions", body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().at("/details/0/field").asText()).isEqualTo("metadata.fee_kind");
    }

    @Test
    void flightWithoutCouponsOrWithHotelMetadataIsRejected() {
        ObjectNode noCoupons = seed("duplicate_flight").put("external_id", "flight-no-coupons");
        assertThat(api.post("/transactions", noCoupons).status()).isEqualTo(400);

        ObjectNode flightAsHotel = seed("flight").put("external_id", "flight-as-hotel");
        flightAsHotel.set("metadata", seed("hotel").get("metadata"));
        assertThat(api.post("/transactions", flightAsHotel).status()).isEqualTo(400);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
    }

    @Test
    void moneyWithMoreThanTwoDecimalsIsRejectedNotRounded() {
        ObjectNode body = seed("trip_fee").put("total", new java.math.BigDecimal("25.001"));

        ApiClient.Response response = api.post("/transactions", body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().at("/details/0/field").asText()).isEqualTo("total");
    }

    @Test
    void getMissingTransactionIs404() {
        assertThat(api.get("/transactions/txn_999999").status()).isEqualTo(404);
        assertThat(api.get("/transactions/not-an-id").status()).isEqualTo(404);
    }

    @Test
    void patchReplacesFieldsOfAnUninvoicedTransaction() {
        String id = createSeed("flight");

        ApiClient.Response response = api.patch("/transactions/" + id, Map.of(
                "total", 230.00,
                "fee_lines", java.util.List.of(Map.of("name", "YQ", "amount", 35.00))));

        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        JsonNode stored = api.get("/transactions/" + id).body();
        assertMoney(stored.get("total"), "230.00");
        assertMoney(stored.at("/fee_lines/0/amount"), "35.00");
        assertThat(stored.at("/tax_lines/0/name").asText()).isEqualTo("Airport tax");
        assertThat(stored.at("/metadata/coupons/0/flight_number").asText()).isEqualTo("BA982");
    }

    @Test
    void patchCannotChangeTypeOrExternalId() {
        String id = createSeed("flight");

        assertThat(api.patch("/transactions/" + id, Map.of("type", "hotel")).status()).isEqualTo(400);
        assertThat(api.patch("/transactions/" + id, Map.of("external_id", "other")).status()).isEqualTo(400);
        assertThat(api.patch("/transactions/txn_999999", Map.of("total", 1)).status()).isEqualTo(404);
    }

    @Test
    void invalidPatchPersistsNothing() {
        String id = createSeed("rail");

        ApiClient.Response response = api.patch("/transactions/" + id, Map.of(
                "total", 99.00,
                "metadata", Map.of("legs", java.util.List.of())));

        assertThat(response.status()).isEqualTo(400);
        JsonNode stored = api.get("/transactions/" + id).body();
        assertMoney(stored.get("total"), "94.00");
        assertThat(stored.at("/metadata/legs")).hasSize(2);
    }

    @Test
    void deleteRemovesAnUninvoicedTransactionAndItsLines() {
        String id = createSeed("flight");
        long rowId = Long.parseLong(id.substring("txn_".length()));

        assertThat(api.delete("/transactions/" + id).status()).isEqualTo(204);

        assertThat(api.get("/transactions/" + id).status()).isEqualTo(404);
        for (String table : java.util.List.of("transaction_tax_lines", "transaction_fee_lines", "flight_details", "flight_coupons")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE transaction_id = ?", Integer.class, rowId))
                    .as(table).isZero();
        }
        assertThat(api.delete("/transactions/" + id).status()).isEqualTo(404);
    }
}
