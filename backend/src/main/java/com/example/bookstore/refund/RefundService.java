package com.example.bookstore.refund;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.alipay.RefundOutcome;
import com.example.bookstore.alipay.RefundResult;
import com.example.bookstore.common.ApiException;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderDto;
import com.example.bookstore.order.OrderService;

/** Refund requests and admin review (design 6.9). */
@Service
public class RefundService {

    private final RefundRequestRepository refunds;
    private final RefundStateService state;
    private final OrderService orderService;
    private final AlipayGateway alipay;

    public RefundService(RefundRequestRepository refunds, RefundStateService state, OrderService orderService,
            AlipayGateway alipay) {
        this.refunds = refunds;
        this.state = state;
        this.orderService = orderService;
        this.alipay = alipay;
    }

    public OrderDto request(Long orderId, String reason) {
        Order order = orderService.require(orderId);
        state.create(order, reason);
        return orderService.get(orderId);
    }

    public List<RefundRequestDto> listAll() {
        return refunds.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(r -> RefundRequestDto.from(r, orderService.get(r.getOrderId())))
                .toList();
    }

    /**
     * Calls Alipay outside any transaction. On failure the request stays PENDING_REVIEW with the error
     * stored, so the admin can retry; the same out_request_no makes the retry idempotent at Alipay.
     */
    public RefundRequestDto approve(Long requestId) {
        RefundRequest request = requirePending(requestId);
        Order order = orderService.require(request.getOrderId());

        RefundResult result = alipay.refund(order.getOrderNo(), request.getRefundAmount(), request.getOutRequestNo(),
                request.getReason());
        if (result.outcome() == RefundOutcome.UNKNOWN) {
            RefundResult confirmed = alipay.refundQuery(order.getOrderNo(), request.getOutRequestNo());
            if (confirmed.outcome() == RefundOutcome.SUCCESS) {
                result = confirmed;
            }
        }
        if (result.outcome() != RefundOutcome.SUCCESS) {
            state.recordError(requestId, result.message());
            throw ApiException.alipay("Alipay refund failed: " + result.message());
        }
        state.completeRefund(request, order);
        return toDto(requestId);
    }

    public RefundRequestDto reject(Long requestId) {
        state.reject(requirePending(requestId));
        return toDto(requestId);
    }

    private RefundRequest requirePending(Long requestId) {
        RefundRequest request = require(requestId);
        if (request.getStatus() != RefundStatus.PENDING_REVIEW) {
            throw ApiException.conflict("INVALID_STATUS", "This refund request has already been reviewed");
        }
        return request;
    }

    private RefundRequest require(Long requestId) {
        return refunds.findById(requestId).orElseThrow(() -> ApiException.notFound("Refund request not found"));
    }

    private RefundRequestDto toDto(Long requestId) {
        RefundRequest request = require(requestId);
        return RefundRequestDto.from(request, orderService.get(request.getOrderId()));
    }
}
