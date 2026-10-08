package com.example.travelbilling.invoice;

import java.util.List;

/** Everything an invoice stores, computed from transactions before it is written. */
public record InvoiceDraft(
        String currency,
        List<InvoiceLine> lines,
        List<TaxRollupLine> taxRollup,
        List<FeeRollupLine> feeRollup,
        InvoiceTotals totals) {
}
