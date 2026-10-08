package com.example.travelbilling.transaction;

import com.example.travelbilling.common.ConflictException;

public class DuplicateExternalIdException extends ConflictException {

    private final TransactionId existingId;

    public DuplicateExternalIdException(String externalId, TransactionId existingId) {
        super("duplicate_external_id", "A transaction with external_id '" + externalId + "' already exists");
        this.existingId = existingId;
    }

    public TransactionId existingId() {
        return existingId;
    }
}
