package com.example.bookstore.alipay;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayDataBillBalanceQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayDataBillBalanceQueryResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;

/** {@link AlipayGateway} backed by the official Alipay SDK. */
@Component
public class SdkAlipayGateway implements AlipayGateway {

    private static final Logger log = LoggerFactory.getLogger(SdkAlipayGateway.class);

    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";
    private static final String SYSTEM_ERROR = "ACQ.SYSTEM_ERROR";

    private final AlipayClient client;
    private final AlipayProperties properties;

    public SdkAlipayGateway(AlipayClient client, AlipayProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String pagePay(PagePayRequest request) {
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(request.outTradeNo());
        model.setTotalAmount(request.totalAmount().toPlainString());
        model.setSubject(request.subject());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeoutExpress(request.timeoutExpress());
        if (request.qrMode()) {
            model.setQrPayMode("2");
        }
        AlipayTradePagePayRequest sdkRequest = new AlipayTradePagePayRequest();
        sdkRequest.setBizModel(model);
        sdkRequest.setNotifyUrl(request.notifyUrl());
        sdkRequest.setReturnUrl(request.returnUrl());
        try {
            AlipayTradePagePayResponse response = client.pageExecute(sdkRequest, "POST");
            if (!response.isSuccess()) {
                throw new AlipayCallException("Alipay rejected the payment request: " + describe(response));
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            throw new AlipayCallException("Failed to create Alipay payment: " + e.getErrMsg(), e);
        }
    }

    @Override
    public TradeQueryResult query(String outTradeNo) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                    return TradeQueryResult.of(TradeState.NOT_EXIST);
                }
                return TradeQueryResult.unknown(describe(response));
            }
            String status = response.getTradeStatus();
            if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
                Date paidAt = response.getSendPayDate();
                return TradeQueryResult.success(response.getTradeNo(), new BigDecimal(response.getTotalAmount()),
                        paidAt != null ? paidAt.toInstant() : null);
            }
            if ("WAIT_BUYER_PAY".equals(status)) {
                return TradeQueryResult.of(TradeState.WAIT_BUYER_PAY);
            }
            if ("TRADE_CLOSED".equals(status)) {
                return TradeQueryResult.of(TradeState.CLOSED);
            }
            return TradeQueryResult.unknown("Unexpected trade status: " + status);
        } catch (AlipayApiException | RuntimeException e) {
            log.warn("alipay.trade.query failed for {}", outTradeNo, e);
            return TradeQueryResult.unknown("Alipay query failed: " + e.getMessage());
        }
    }

    @Override
    public void close(String outTradeNo) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(outTradeNo);
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (!response.isSuccess() && !TRADE_NOT_EXIST.equals(response.getSubCode())) {
                throw new AlipayCallException("Alipay could not close the trade: " + describe(response));
            }
        } catch (AlipayApiException e) {
            throw new AlipayCallException("Failed to close Alipay trade: " + e.getErrMsg(), e);
        }
    }

    @Override
    public RefundResult refund(String outTradeNo, BigDecimal amount, String outRequestNo, String reason) {
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(outTradeNo);
        model.setRefundAmount(amount.toPlainString());
        model.setOutRequestNo(outRequestNo);
        model.setRefundReason(reason);
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (response.isSuccess()) {
                // Code 10000 also covers a repeated request for a refund that already succeeded.
                return RefundResult.success();
            }
            if (SYSTEM_ERROR.equals(response.getSubCode()) || response.getSubCode() == null) {
                return RefundResult.unknown(describe(response));
            }
            return RefundResult.failed(describe(response));
        } catch (AlipayApiException | RuntimeException e) {
            log.warn("alipay.trade.refund failed for {}", outTradeNo, e);
            return RefundResult.unknown("Alipay refund call failed: " + e.getMessage());
        }
    }

    @Override
    public RefundResult refundQuery(String outTradeNo, String outRequestNo) {
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(outTradeNo);
        model.setOutRequestNo(outRequestNo);
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                return RefundResult.unknown(describe(response));
            }
            // An empty refund status means no refund exists for this request number.
            if ("REFUND_SUCCESS".equals(response.getRefundStatus())) {
                return RefundResult.success();
            }
            return RefundResult.failed("Refund not found at Alipay");
        } catch (AlipayApiException | RuntimeException e) {
            log.warn("alipay.trade.fastpay.refund.query failed for {}", outTradeNo, e);
            return RefundResult.unknown("Alipay refund query failed: " + e.getMessage());
        }
    }

    @Override
    public Optional<BigDecimal> balanceQuery() {
        try {
            AlipayDataBillBalanceQueryResponse response = client.execute(new AlipayDataBillBalanceQueryRequest());
            if (!response.isSuccess() || response.getTotalAmount() == null) {
                log.info("alipay.data.bill.balance.query unavailable: {}", describe(response));
                return Optional.empty();
            }
            return Optional.of(new BigDecimal(response.getTotalAmount()));
        } catch (AlipayApiException | RuntimeException e) {
            log.info("alipay.data.bill.balance.query failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean verifySignature(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params, properties.alipayPublicKey(), AlipayConfiguration.CHARSET,
                    AlipayConfiguration.SIGN_TYPE);
        } catch (AlipayApiException | RuntimeException e) {
            log.warn("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Summarizes an error response using only the code fields. sub_msg is left out because Alipay
     * returns it in Chinese and this text is shown in the English UI.
     */
    private static String describe(AlipayResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(response.getCode()).append(' ').append(response.getMsg());
        if (response.getSubCode() != null) {
            sb.append(" (").append(response.getSubCode()).append(')');
        }
        return sb.toString();
    }
}
