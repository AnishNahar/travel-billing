package com.example.travelbilling.transaction.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record FlightMetadata(
        @NotBlank @Size(max = 64) String origin,
        @NotBlank @Size(max = 64) String destination,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal fare,
        @NotEmpty @Valid List<FlightCoupon> coupons) implements TransactionMetadata {
}
