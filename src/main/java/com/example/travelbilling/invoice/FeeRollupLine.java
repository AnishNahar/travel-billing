package com.example.travelbilling.invoice;

import java.math.BigDecimal;

/** Fees summed across the invoice, grouped by name. */
public record FeeRollupLine(String name, BigDecimal amount) {
}
