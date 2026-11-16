package com.example.bookstore.payment;

import com.example.bookstore.order.OrderStatus;

/**
 * @param status   PENDING_PAYMENT when {@code formHtml} should be submitted, PAID if it turned out the
 *                 order was already paid
 * @param formHtml auto-submit form that sends the browser to Alipay; null when already paid
 */
public record PayResponse(OrderStatus status, String formHtml) {
}
