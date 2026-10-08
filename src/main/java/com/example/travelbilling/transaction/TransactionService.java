package com.example.travelbilling.transaction;

import com.example.travelbilling.common.ConflictException;
import com.example.travelbilling.common.NotFoundException;
import com.example.travelbilling.common.PageRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionRequestReader reader;
    private final ExternalIdGuard externalIdGuard;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, TransactionRequestReader reader,
                              ExternalIdGuard externalIdGuard, Clock clock) {
        this.repository = repository;
        this.reader = reader;
        this.externalIdGuard = externalIdGuard;
        this.clock = clock;
    }

    /** Identity is checked before the body is validated, so a retried booking always gets 409 + the existing id. */
    public Transaction create(JsonNode body) {
        String externalId = reader.requireExternalId(body);
        externalIdGuard.rejectIfTaken(externalId);
        TransactionData data = reader.read(body);
        TransactionId id = externalIdGuard.insertOnce(externalId, () -> repository.insert(data, now()));
        return get(id);
    }

    public Transaction get(TransactionId id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    public List<TransactionSummary> list(PageRequest page) {
        return repository.list(page);
    }

    /** The row lock serialises this against invoice creation, which locks the same rows. */
    @Transactional
    public Transaction update(TransactionId id, JsonNode patch) {
        lockAndRejectIfInvoiced(id, "updated");
        TransactionData data = reader.readPatch(repository.findById(id).orElseThrow(), patch);
        repository.replace(id, data, now());
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public void delete(TransactionId id) {
        lockAndRejectIfInvoiced(id, "deleted");
        repository.delete(id);
    }

    private void lockAndRejectIfInvoiced(TransactionId id, String action) {
        if (!repository.lockForUpdate(id)) {
            throw notFound(id);
        }
        if (repository.isOnAnyInvoice(id)) {
            throw new ConflictException("transaction_invoiced",
                    "Transaction " + id + " is on an invoice and cannot be " + action);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static NotFoundException notFound(TransactionId id) {
        return new NotFoundException("Transaction " + id + " not found");
    }
}
