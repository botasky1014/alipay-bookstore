package com.example.bookstore.settings;

import java.time.Instant;

/** The two Alipay callback URLs; an empty string means not configured yet. */
public record AlipayUrlsDto(String notifyUrl, Instant notifyUrlUpdatedAt, String returnUrl,
        Instant returnUrlUpdatedAt) {
}
