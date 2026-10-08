package com.example.travelbilling.invoice;

import com.example.travelbilling.transaction.TransactionId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Invoice(
        InvoiceId id,
        String currency,
        List<InvoiceLine> lines,
        List<TaxRollupLine> taxRollup,
        List<FeeRollupLine> feeRollup,
        InvoiceTotals totals,
        Instant createdAt,
        Instant updatedAt) {

    @JsonProperty("transaction_ids")
    public List<TransactionId> transactionIds() {
        return lines.stream().map(InvoiceLine::transactionId).toList();
    }
}
