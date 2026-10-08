package com.example.travelbilling.transaction.metadata;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A Navan-owned charge on the trip (trip fee, agent call fee). */
public record NavanFeeMetadata(
        @NotNull FeeKind feeKind,
        @Size(max = 500) String description,
        @Size(max = 255) String relatedExternalId) implements TransactionMetadata {
}
