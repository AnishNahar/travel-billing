package com.example.travelbilling.invoice;

import com.example.travelbilling.common.PublicIds;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public record InvoiceId(long value) {

    private static final String PREFIX = "inv_";

    public static Optional<InvoiceId> parse(String raw) {
        return PublicIds.parse(PREFIX, raw).map(InvoiceId::new);
    }

    @JsonValue
    @Override
    public String toString() {
        return PREFIX + value;
    }
}
