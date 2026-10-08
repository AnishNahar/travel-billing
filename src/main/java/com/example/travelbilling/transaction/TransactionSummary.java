package com.example.travelbilling.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** Header-only row used by the list endpoint (one indexed query, no child-table reads). */
public record TransactionSummary(
        TransactionId id,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt) {
}
