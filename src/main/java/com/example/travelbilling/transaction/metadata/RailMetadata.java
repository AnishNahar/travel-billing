package com.example.travelbilling.transaction.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** A rail journey. A change of train is two legs. */
public record RailMetadata(@NotEmpty @Valid List<RailLeg> legs) implements TransactionMetadata {
}
