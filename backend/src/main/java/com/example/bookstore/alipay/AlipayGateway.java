package com.example.bookstore.alipay;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * The only entry point to Alipay. Business code depends on this interface, never on the SDK,
 * so tests can replace it with a mock.
 */
public interface AlipayGateway {

    /**
     * Builds the auto-submit HTML form for {@code alipay.trade.page.pay}.
     *
     * @throws AlipayCallException if the form cannot be built
     */
    String pagePay(PagePayRequest request);

    /** {@code alipay.trade.query}. Never throws; failures map to {@link TradeState#UNKNOWN}. */
    TradeQueryResult query(String outTradeNo);

    /**
     * {@code alipay.trade.close}. A trade that does not exist is treated as closed.
     *
     * @throws AlipayCallException if Alipay did not confirm the close
     */
    void close(String outTradeNo);

    /** {@code alipay.trade.refund}. Never throws. */
    RefundResult refund(String outTradeNo, BigDecimal amount, String outRequestNo, String reason);

    /** {@code alipay.trade.fastpay.refund.query}. Never throws. */
    RefundResult refundQuery(String outTradeNo, String outRequestNo);

    /** {@code alipay.data.bill.balance.query}; empty when unavailable. */
    Optional<BigDecimal> balanceQuery();

    /** Verifies the RSA2 signature of notification or return parameters. */
    boolean verifySignature(Map<String, String> params);
}
