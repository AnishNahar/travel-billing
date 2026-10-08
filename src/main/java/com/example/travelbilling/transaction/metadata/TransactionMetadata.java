package com.example.travelbilling.transaction.metadata;

/** Type-specific part of a transaction. Each subtype is stored in its own table(s). */
public sealed interface TransactionMetadata
        permits FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata {
}
