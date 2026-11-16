package com.example.bookstore.fund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FundTransactionRepository extends JpaRepository<FundTransaction, Long> {

    List<FundTransaction> findAllByOrderByOccurredAtDescIdDesc();

    /**
     * Inserts a fund transaction unless one of the same type already exists for the order.
     * This makes duplicate notifications and retries safe.
     *
     * @return 1 if inserted, 0 if it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "insert into fund_transactions (type, order_id, amount, alipay_trade_no, occurred_at) "
            + "values (:type, :orderId, :amount, :tradeNo, :occurredAt) "
            + "on conflict (order_id, type) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("type") String type, @Param("orderId") Long orderId,
            @Param("amount") BigDecimal amount, @Param("tradeNo") String tradeNo,
            @Param("occurredAt") Instant occurredAt);

    @Query("select coalesce(sum(f.amount), 0) from FundTransaction f where f.type = :type")
    BigDecimal sumByType(@Param("type") FundType type);
}
