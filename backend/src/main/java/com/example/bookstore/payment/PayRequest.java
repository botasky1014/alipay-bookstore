package com.example.bookstore.payment;

import jakarta.validation.constraints.NotNull;

public record PayRequest(@NotNull PayMethod method) {
}
