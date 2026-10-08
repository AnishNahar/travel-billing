package com.example.travelbilling.transaction;

import com.example.travelbilling.transaction.metadata.TransactionMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A validated transaction as it will be written (no server-assigned fields yet). */
public record TransactionData(
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata) {
}
