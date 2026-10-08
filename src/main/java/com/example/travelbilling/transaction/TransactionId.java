package com.example.travelbilling.transaction;

import com.example.travelbilling.common.PublicIds;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public record TransactionId(long value) {

    private static final String PREFIX = "txn_";

    public static Optional<TransactionId> parse(String raw) {
        return PublicIds.parse(PREFIX, raw).map(TransactionId::new);
    }

    @JsonValue
    @Override
    public String toString() {
        return PREFIX + value;
    }
}
