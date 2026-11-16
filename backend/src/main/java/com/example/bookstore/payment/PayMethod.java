package com.example.bookstore.payment;

public enum PayMethod {
    /** Standard Alipay cashier page (log in with an Alipay account). */
    WEB,
    /** Alipay QR code page ({@code qr_pay_mode=2}). */
    QR
}
