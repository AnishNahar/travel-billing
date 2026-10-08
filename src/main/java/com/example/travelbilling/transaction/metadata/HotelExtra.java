package com.example.travelbilling.transaction.metadata;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Informational breakdown of the stay (breakfast, parking). Already included in the transaction total. */
public record HotelExtra(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
