package com.example.travelbilling.invoice;

import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** One billed transaction. {@code snapshot} is the full transaction as it was stored when the invoice was built. */
public record InvoiceLine(
        int lineNo,
        TransactionId transactionId,
        String externalId,
        TransactionType type,
        BigDecimal total,
        JsonNode snapshot) {

    /** flight / hotel / rail, or the fee_kind (trip_fee, agent_call_fee) for Navan fees. Keys the per-kind totals. */
    @JsonProperty("kind")
    public String kind() {
        return type == TransactionType.NAVAN_FEE ? snapshot.at("/metadata/fee_kind").asText() : type.wireName();
    }
}
