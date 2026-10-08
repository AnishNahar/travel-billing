package com.example.travelbilling.transaction;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A carrier/merchant fee on a transaction (YQ surcharge, booking fee, ...). */
public record FeeLine(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
