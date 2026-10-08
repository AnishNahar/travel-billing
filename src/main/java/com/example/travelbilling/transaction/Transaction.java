package com.example.travelbilling.transaction;

import com.example.travelbilling.transaction.metadata.TransactionMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A stored transaction, exactly as returned by GET /transactions/{id} and copied into invoice snapshots. */
public record Transaction(
        TransactionId id,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata,
        Instant createdAt,
        Instant updatedAt) {
}
