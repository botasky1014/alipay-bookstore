package com.example.bookstore.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username) {
}
