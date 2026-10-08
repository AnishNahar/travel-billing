package com.example.travelbilling;

import com.example.travelbilling.common.ConflictException;
import com.example.travelbilling.invoice.InvoiceAssembler;
import com.example.travelbilling.invoice.InvoiceDraft;
import com.example.travelbilling.transaction.FeeLine;
import com.example.travelbilling.transaction.TaxLine;
import com.example.travelbilling.transaction.Transaction;
import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionType;
import com.example.travelbilling.transaction.metadata.FeeKind;
import com.example.travelbilling.transaction.metadata.NavanFeeMetadata;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceAssemblerTest {

    private final InvoiceAssembler assembler = new InvoiceAssembler(JsonMapper.builder().findAndAddModules().build());

    @Test
    void taxesGroupByNameAndRateAndFeesByName() {
        InvoiceDraft draft = assembler.assemble(List.of(
                transaction(1, "EUR", "100.10",
                        List.of(new TaxLine("VAT", new BigDecimal("0.07"), new BigDecimal("7.00")),
                                new TaxLine("VAT", new BigDecimal("0.19"), new BigDecimal("19.00"))),
                        List.of(new FeeLine("Booking fee", new BigDecimal("1.10")))),
                transaction(2, "EUR", "0.20",
                        List.of(new TaxLine("VAT", new BigDecimal("0.070000"), new BigDecimal("0.10"))),
                        List.of(new FeeLine("Booking fee", new BigDecimal("2.20"))))));

        assertThat(draft.taxRollup()).hasSize(2);
        assertThat(draft.taxRollup().get(0).rate()).isEqualByComparingTo("0.07");
        assertThat(draft.taxRollup().get(0).amount()).isEqualByComparingTo("7.10");
        assertThat(draft.taxRollup().get(1).rate()).isEqualByComparingTo("0.19");
        assertThat(draft.feeRollup()).hasSize(1);
        assertThat(draft.feeRollup().getFirst().amount()).isEqualByComparingTo("3.30");
        // 100.10 + 0.20 is exactly 100.30 with BigDecimal (with doubles it is 100.30000000000001).
        assertThat(draft.totals().grandTotal()).isEqualTo(new BigDecimal("100.30"));
        assertThat(draft.totals().taxTotal()).isEqualTo(new BigDecimal("26.10"));
    }

    @Test
    void mixedCurrencyIsAConflict() {
        assertThatThrownBy(() -> assembler.assemble(List.of(
                transaction(1, "EUR", "1.00", List.of(), List.of()),
                transaction(2, "USD", "1.00", List.of(), List.of()))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    private static Transaction transaction(long id, String currency, String total, List<TaxLine> taxes, List<FeeLine> fees) {
        Instant now = Instant.parse("2026-03-11T00:00:00Z");
        return new Transaction(new TransactionId(id), TransactionType.NAVAN_FEE, "ext-" + id, currency, now,
                new BigDecimal(total), taxes, fees, new NavanFeeMetadata(FeeKind.TRIP_FEE, null, null), now, now);
    }
}
