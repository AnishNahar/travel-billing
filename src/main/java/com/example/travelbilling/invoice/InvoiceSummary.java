package com.example.travelbilling.invoice;

import java.math.BigDecimal;
import java.time.Instant;

/** Header-only row used by the list endpoint. */
public record InvoiceSummary(
        InvoiceId id,
        String currency,
        int lineCount,
        BigDecimal grandTotal,
        Instant createdAt,
        Instant updatedAt) {
}
