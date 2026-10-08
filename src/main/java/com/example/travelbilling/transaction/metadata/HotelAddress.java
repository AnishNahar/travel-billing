package com.example.travelbilling.transaction.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelAddress(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 128) String city,
        @Size(max = 32) String postalCode,
        @NotBlank @Size(max = 64) String country) {
}
