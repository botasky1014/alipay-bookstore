package com.example.bookstore.settings;

import jakarta.validation.constraints.NotBlank;

public record UpdateAlipayUrlsRequest(@NotBlank String notifyUrl, @NotBlank String returnUrl) {
}
