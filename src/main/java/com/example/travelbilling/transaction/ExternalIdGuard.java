package com.example.travelbilling.transaction;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Duplicate identity: one stored transaction per external_id.
 * The pre-check is only a fast path; the unique constraint on transactions.external_id is what
 * makes two overlapping POSTs safe — the loser's insert fails and is turned into a 409.
 */
@Component
public class ExternalIdGuard {

    private final TransactionRepository repository;
    private final TransactionTemplate transactionTemplate;

    public ExternalIdGuard(TransactionRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    public void rejectIfTaken(String externalId) {
        repository.findIdByExternalId(externalId).ifPresent(existing -> {
            throw new DuplicateExternalIdException(externalId, existing);
        });
    }

    /** Runs {@code insert} in its own DB transaction; header and child rows commit together or not at all. */
    public TransactionId insertOnce(String externalId, Supplier<TransactionId> insert) {
        try {
            return transactionTemplate.execute(status -> insert.get());
        } catch (DuplicateKeyException e) {
            throw new DuplicateExternalIdException(externalId, repository.findIdByExternalId(externalId).orElse(null));
        }
    }
}
