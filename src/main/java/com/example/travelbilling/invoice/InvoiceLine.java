package com.example.travelbilling.invoice;

import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionType;
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
}
