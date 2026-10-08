package com.example.travelbilling.transaction.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** One flight segment. A round trip has (at least) two coupons. */
public record FlightCoupon(
        @NotBlank @Size(max = 16) String flightNumber,
        @Size(max = 8) String carrier,
        @NotBlank @Size(max = 64) String from,
        @NotBlank @Size(max = 64) String to,
        @NotNull Instant departure,
        @NotNull Instant arrival) {
}
