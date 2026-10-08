package com.example.travelbilling.transaction.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RailLeg(
        @NotBlank @Size(max = 128) String origin,
        @NotBlank @Size(max = 128) String destination,
        @NotNull Instant departure,
        @NotNull Instant arrival,
        @Size(max = 32) String trainNumber,
        @JsonProperty("class") @Size(max = 32) String travelClass) {
}
