package com.example.bookstore.alipay;

import java.math.BigDecimal;

/**
 * Input for {@code alipay.trade.page.pay}.
 *
 * @param qrMode true for QR code payment ({@code qr_pay_mode=2}), false for the standard cashier page
 */
public record PagePayRequest(String outTradeNo, BigDecimal totalAmount, String subject, String notifyUrl,
        String returnUrl, String timeoutExpress, boolean qrMode) {
}
