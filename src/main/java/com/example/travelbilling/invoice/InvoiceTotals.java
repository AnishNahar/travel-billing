package com.example.travelbilling.invoice;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-kind totals (flight, hotel, rail, trip_fee, agent_call_fee) are written as top-level keys, as in
 * expected-invoice.json. grand_total is the sum of the gross transaction totals; tax_total and fee_total are inside it.
 */
public record InvoiceTotals(
        @JsonIgnore Map<String, BigDecimal> byKind,
        BigDecimal taxTotal,
        BigDecimal feeTotal,
        BigDecimal grandTotal) {

    /** Sums snapshot line totals per kind, in first-appearance order. */
    public static Map<String, BigDecimal> byKind(List<InvoiceLine> lines) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        lines.forEach(line -> totals.merge(line.kind(), line.total(), BigDecimal::add));
        return totals;
    }

    @JsonAnyGetter
    public Map<String, BigDecimal> kindTotals() {
        return byKind;
    }
}
