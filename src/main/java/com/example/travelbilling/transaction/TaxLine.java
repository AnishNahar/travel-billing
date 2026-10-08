package com.example.travelbilling.transaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** A government/VAT-style tax. {@code rate} is optional and expressed as a fraction (0.07 = 7 %). */
public record TaxLine(
        @NotBlank @Size(max = 255) String name,
        @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 6) BigDecimal rate,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount) {
}
