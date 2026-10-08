package com.example.travelbilling.transaction.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum FeeKind {
    TRIP_FEE("trip_fee"),
    AGENT_CALL_FEE("agent_call_fee");

    private final String wireName;

    FeeKind(String wireName) {
        this.wireName = wireName;
    }

    @JsonCreator
    public static FeeKind fromWire(String value) {
        return Arrays.stream(values())
                .filter(k -> k.wireName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("must be one of trip_fee, agent_call_fee"));
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}
