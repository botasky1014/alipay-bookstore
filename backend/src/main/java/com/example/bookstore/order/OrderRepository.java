package com.example.bookstore.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * All status changes are conditional updates ("... where status = :from"). A return value of 0
 * means the order was not in the expected status (concurrent change or duplicate processing).
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Order> findByStatusAndPayRequestedAtIsNotNullOrderByIdAsc(OrderStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = :to, o.updatedAt = :now where o.id = :id and o.status = :from")
    int transition(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = com.example.bookstore.order.OrderStatus.PAID, "
            + "o.alipayTradeNo = :tradeNo, o.paidAt = :paidAt, o.updatedAt = :now "
            + "where o.id = :id and o.status = com.example.bookstore.order.OrderStatus.PENDING_PAYMENT")
    int markPaid(@Param("id") Long id, @Param("tradeNo") String tradeNo, @Param("paidAt") Instant paidAt,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = com.example.bookstore.order.OrderStatus.CLOSED, "
            + "o.closeReason = :reason, o.closedAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status = com.example.bookstore.order.OrderStatus.PENDING_PAYMENT")
    int markClosed(@Param("id") Long id, @Param("reason") String reason, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.payRequestedAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status = com.example.bookstore.order.OrderStatus.PENDING_PAYMENT")
    int markPayRequested(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = com.example.bookstore.order.OrderStatus.COMPLETED, "
            + "o.completedAt = :now, o.updatedAt = :now where o.id = :id and o.status = :from")
    int markCompleted(@Param("id") Long id, @Param("from") OrderStatus from, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Order o set o.status = com.example.bookstore.order.OrderStatus.COMPLETED, "
            + "o.completedAt = :now, o.updatedAt = :now "
            + "where o.status = com.example.bookstore.order.OrderStatus.PAID and o.paidAt <= :paidBefore")
    int completePaidBefore(@Param("paidBefore") Instant paidBefore, @Param("now") Instant now);
}
