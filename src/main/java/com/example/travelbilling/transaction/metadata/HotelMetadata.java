package com.example.travelbilling.transaction.metadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HotelMetadata(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Valid HotelAddress address,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Positive Integer nights,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal roomRatePerNight,
        @Valid List<HotelExtra> extras) implements TransactionMetadata {

    public HotelMetadata {
        extras = extras == null ? List.of() : extras;
    }
}
