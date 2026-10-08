package com.example.travelbilling.invoice;

import java.math.BigDecimal;

/** Taxes summed across the invoice, grouped by (name, rate). */
public record TaxRollupLine(String name, BigDecimal rate, BigDecimal amount) {
}
