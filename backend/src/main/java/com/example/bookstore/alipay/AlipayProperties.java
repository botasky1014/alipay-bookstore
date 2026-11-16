package com.example.bookstore.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Static Alipay settings from environment variables (see .env). The notify URL and return URL are
 * not here: they are dynamic and read from the database on every payment (SettingsService).
 */
@ConfigurationProperties("alipay")
public record AlipayProperties(
        String appId,
        String sellerId,
        String gateway,
        String appPrivateKey,
        String alipayPublicKey,
        String timeoutExpress) {
}
