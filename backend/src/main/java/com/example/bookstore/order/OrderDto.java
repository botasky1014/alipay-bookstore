package com.example.bookstore.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.bookstore.refund.RefundRequest;
import com.example.bookstore.refund.RefundStatus;

public record OrderDto(Long id, String orderNo, Long userId, OrderStatus status, BigDecimal totalAmount,
        List<Item> items, Refund refund, String closeReason, Instant createdAt, Instant paidAt,
        Instant completedAt, Instant closedAt) {

    public record Item(Long bookId, String title, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static Item from(OrderItem item) {
            return new Item(item.getBookId(), item.getTitleSnapshot(), item.getUnitPriceSnapshot(),
                    item.getQuantity(), item.getSubtotal());
        }
    }

    public record Refund(Long id, RefundStatus status, String reason, Instant createdAt, Instant reviewedAt) {

        static Refund from(RefundRequest request) {
            return request == null ? null
                    : new Refund(request.getId(), request.getStatus(), request.getReason(), request.getCreatedAt(),
                            request.getReviewedAt());
        }
    }

    public static OrderDto from(Order order, List<OrderItem> items, RefundRequest refund) {
        return new OrderDto(order.getId(), order.getOrderNo(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), items.stream().map(Item::from).toList(), Refund.from(refund),
                order.getCloseReason(), order.getCreatedAt(), order.getPaidAt(), order.getCompletedAt(),
                order.getClosedAt());
    }
}
