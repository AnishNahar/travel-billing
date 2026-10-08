package com.example.travelbilling.invoice;

import java.math.BigDecimal;

/** grand_total is the sum of the gross transaction totals; tax_total and fee_total are already inside it. */
public record InvoiceTotals(BigDecimal taxTotal, BigDecimal feeTotal, BigDecimal grandTotal) {
}
