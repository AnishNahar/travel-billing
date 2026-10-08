package com.example.travelbilling.invoice;

import com.example.travelbilling.common.ConflictException;
import com.example.travelbilling.common.NotFoundException;
import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.transaction.Transaction;
import com.example.travelbilling.transaction.TransactionId;
import com.example.travelbilling.transaction.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class InvoiceService {

    private final InvoiceRepository repository;
    private final TransactionRepository transactions;
    private final InvoiceRequestReader reader;
    private final InvoiceAssembler assembler;
    private final Clock clock;

    public InvoiceService(InvoiceRepository repository, TransactionRepository transactions,
                          InvoiceRequestReader reader, InvoiceAssembler assembler, Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.reader = reader;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public Invoice create(JsonNode body) {
        InvoiceDraft draft = snapshot(reader.readTransactionIds(body));
        InvoiceId id = repository.insert(draft, now());
        return repository.findById(id).orElseThrow();
    }

    public Invoice get(InvoiceId id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    public List<InvoiceSummary> list(PageRequest page) {
        return repository.list(page);
    }

    /** Rebuilds the snapshot from the current stored transactions; any failure leaves the invoice as it was. */
    @Transactional
    public Invoice update(InvoiceId id, JsonNode body) {
        List<String> requested = reader.readTransactionIds(body);
        if (!repository.lockForUpdate(id)) {
            throw notFound(id);
        }
        repository.replace(id, snapshot(requested), now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(InvoiceId id) {
        if (!repository.lockForUpdate(id)) {
            throw notFound(id);
        }
        repository.delete(id);
    }

    /**
     * Locks every requested transaction (ascending id order, so overlapping invoices cannot deadlock),
     * then copies them. A concurrent PATCH/DELETE of one of them waits for us and then sees it is invoiced.
     */
    private InvoiceDraft snapshot(List<String> requested) {
        List<String> missing = new ArrayList<>();
        List<TransactionId> ids = new ArrayList<>();
        for (String raw : requested) {
            Optional<TransactionId> id = TransactionId.parse(raw);
            id.ifPresentOrElse(ids::add, () -> missing.add(raw));
        }
        rejectDuplicates(ids);

        ids.stream()
                .sorted(Comparator.comparingLong(TransactionId::value))
                .filter(id -> !transactions.lockForUpdate(id))
                .forEach(id -> missing.add(id.toString()));
        if (!missing.isEmpty()) {
            throw new NotFoundException("Transactions not found: " + missing);
        }

        List<Transaction> loaded = ids.stream().map(id -> transactions.findById(id).orElseThrow()).toList();
        return assembler.assemble(loaded);
    }

    private static void rejectDuplicates(List<TransactionId> ids) {
        Set<TransactionId> seen = new HashSet<>();
        List<TransactionId> duplicates = ids.stream().filter(id -> !seen.add(id)).distinct().toList();
        if (!duplicates.isEmpty()) {
            throw new ConflictException("duplicate_transaction_id",
                    "A transaction can appear only once on an invoice: " + duplicates);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static NotFoundException notFound(InvoiceId id) {
        return new NotFoundException("Invoice " + id + " not found");
    }
}
