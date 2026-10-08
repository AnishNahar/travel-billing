package com.example.travelbilling.invoice;

import com.example.travelbilling.common.ConflictException;
import com.example.travelbilling.common.Money;
import com.example.travelbilling.transaction.FeeLine;
import com.example.travelbilling.transaction.TaxLine;
import com.example.travelbilling.transaction.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Pure invoice maths: copies each transaction into a snapshot line and rolls up taxes and fees.
 * Rollup order is first appearance, so the output is deterministic for a given transaction order.
 */
@Component
public class InvoiceAssembler {

    private final ObjectMapper mapper;

    public InvoiceAssembler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public InvoiceDraft assemble(List<Transaction> transactions) {
        SortedSet<String> currencies = transactions.stream()
                .map(Transaction::currency)
                .collect(Collectors.toCollection(TreeSet::new));
        if (currencies.size() != 1) {
            throw new ConflictException("mixed_currency",
                    "All transactions on an invoice must share one currency, got " + currencies);
        }

        List<InvoiceLine> lines = new ArrayList<>();
        Map<TaxKey, BigDecimal> taxes = new LinkedHashMap<>();
        Map<String, BigDecimal> fees = new LinkedHashMap<>();
        BigDecimal grandTotal = Money.zero();

        for (Transaction transaction : transactions) {
            lines.add(new InvoiceLine(lines.size() + 1, transaction.id(), transaction.externalId(),
                    transaction.type(), transaction.total(), mapper.valueToTree(transaction)));
            grandTotal = grandTotal.add(transaction.total());
            for (TaxLine tax : transaction.taxLines()) {
                taxes.merge(new TaxKey(tax.name(), tax.rate() == null ? null : tax.rate().stripTrailingZeros()),
                        tax.amount(), BigDecimal::add);
            }
            for (FeeLine fee : transaction.feeLines()) {
                fees.merge(fee.name(), fee.amount(), BigDecimal::add);
            }
        }

        List<TaxRollupLine> taxRollup = taxes.entrySet().stream()
                .map(e -> new TaxRollupLine(e.getKey().name(), e.getKey().rate(), Money.normalize(e.getValue())))
                .toList();
        List<FeeRollupLine> feeRollup = fees.entrySet().stream()
                .map(e -> new FeeRollupLine(e.getKey(), Money.normalize(e.getValue())))
                .toList();
        BigDecimal taxTotal = taxRollup.stream().map(TaxRollupLine::amount).reduce(Money.zero(), BigDecimal::add);
        BigDecimal feeTotal = feeRollup.stream().map(FeeRollupLine::amount).reduce(Money.zero(), BigDecimal::add);

        return new InvoiceDraft(currencies.first(), lines, taxRollup, feeRollup,
                new InvoiceTotals(taxTotal, feeTotal, Money.normalize(grandTotal)));
    }

    private record TaxKey(String name, BigDecimal rate) {
    }
}
