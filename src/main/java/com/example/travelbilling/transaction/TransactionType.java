package com.example.travelbilling.transaction;

import com.example.travelbilling.transaction.metadata.FlightMetadata;
import com.example.travelbilling.transaction.metadata.HotelMetadata;
import com.example.travelbilling.transaction.metadata.NavanFeeMetadata;
import com.example.travelbilling.transaction.metadata.RailMetadata;
import com.example.travelbilling.transaction.metadata.TransactionMetadata;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

public enum TransactionType {
    FLIGHT("flight", FlightMetadata.class),
    HOTEL("hotel", HotelMetadata.class),
    RAIL("rail", RailMetadata.class),
    NAVAN_FEE("navan_fee", NavanFeeMetadata.class);

    private final String wireName;
    private final Class<? extends TransactionMetadata> metadataClass;

    TransactionType(String wireName, Class<? extends TransactionMetadata> metadataClass) {
        this.wireName = wireName;
        this.metadataClass = metadataClass;
    }

    public static Optional<TransactionType> fromWire(String value) {
        return Arrays.stream(values()).filter(t -> t.wireName.equals(value)).findFirst();
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    public Class<? extends TransactionMetadata> metadataClass() {
        return metadataClass;
    }
}
