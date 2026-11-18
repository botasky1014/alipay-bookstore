package com.example.bookstore.alipay;

import java.math.BigDecimal;

/**
 * Input for {@code alipay.trade.page.pay}.
 */
public record PagePayRequest(String outTradeNo, BigDecimal totalAmount, String subject, String notifyUrl,
        String returnUrl, String timeoutExpress) {
}
