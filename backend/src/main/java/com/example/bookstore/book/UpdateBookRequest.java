package com.example.bookstore.book;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

/** All fields are optional; only the ones present are changed. */
public record UpdateBookRequest(
        Integer stockDelta,
        @DecimalMin(value = "0.01", message = "must be greater than 0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(max = 255) String title) {
}
